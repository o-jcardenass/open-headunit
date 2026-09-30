# wifi-direct-stable-identity, round 1: does the group keep its name, and does this unit keep its address?

**Candidate:** `fork/feat/wifi-direct-stable-identity` @ `652ba40f` (3 commits on `ef74866c`), `3.3.0`
**Baseline:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `ef74866c`, the candidate's own parent. The A/B isolates exactly the three commits.

```bash
git fetch fork
git rev-parse fork/feat/wifi-direct-stable-identity        # 652ba40f...
git rev-parse fork/feat/native-aa-wpp-tcp-and-hfp-link     # ef74866c...  (9 commits on a7076ff4; it absorbed the Self Mode and stand-down branch on 2026-09-01)
```

| SHA | What |
|---|---|
| `a0940339` | The Native AA group is asked for as persistent, with a name and passphrase drawn once and kept; the ten-second credential refresh reads a live group again instead of remaking it |
| `8dc5f92e` | Whether the group's BSSID repeats is measured across bring-ups and travels with the credentials |
| `652ba40f` | The WPP-over-TCP endpoint is offered on WiFi Direct once that measurement says stable |

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific to the branch.

---

## 1. Why this round exists

Every release through 3.1.1 created the Native AA group with the two-argument `createGroup`, which the platform answers with its stored profile, so the group came back under the same SSID and passphrase every time and the phone rejoined a network it had saved. 3.2.0 replaced that with a `WifiP2pConfig` that named the network randomly on every create and, with no `enablePersistentMode`, built a temporary group. The phone has been set up for a brand-new network on every session since, and the handshake's ten-second credential refresh was a full teardown and recreate, so a slow bring-up rotated the SSID while the phone was trying to join it.

The candidate keeps one name and passphrase in `Settings`, asks for the group as persistent, and reads a live group again on refresh instead of remaking it. What it cannot promise is the BSSID: AOSP re-randomizes the group's own address on every create wherever P2P MAC randomization is on, and whether it is on is a per-unit configuration the app cannot read. So the branch measures it, across two bring-ups, and gates the TCP endpoint on the answer.

**A prior from this rig.** `five-ghz-channel` round 2's groups on this unit carried different BSSIDs (`0E:5E:04:CB:87:92` on `p2p-wlan0-1`, `c2:f7:6a:32:cf:c5` on `p2p-wlan0-3`, both with the locally-administered bit set), so the MT50 most likely re-randomizes the group address on every create. Those were temporary groups; whether a *persistent* one keeps its interface address is exactly what R1 measures. Expect `stable=no`, in which case R4b is the live run of this round and R4 is a pass-by-refusal.

Three questions, in order of value:

1. **Does the platform honour the request on this unit?** Same SSID and passphrase across five bring-ups, and `persistent=yes` in the read-back line. If the platform ignores `enablePersistentMode`, the line says `persistent=no (temporary)` and the whole branch is inert on this hardware.
2. **Does the BSSID repeat on this unit?** This is the one thing nobody can predict. Its answer decides whether R4 can run at all.
3. **Is a reconnect faster, and does the group stop churning?** R2 and R3, A/B against the parent.

Nothing in the candidate touches the Helper path's `checkGroupAndCreate`, the hotspot transport, or the teardown before every create. The "never reuse a P2P group" rule still holds; only the identity handed to the create changed.

---

## 2. What is different about this round

- **One phone-side step decides the whole round: forget the head unit first.** A phone that has ever seen a `port=5299` advertisement keeps it for the life of its Gearhead process and dials it instead of running Bluetooth. Step 0 below, and confirm it on the first connect.
- **The round is mostly head-unit-only.** R1, R3 and R6 need no phone at all: keep the phone's Bluetooth off for them so its own reconnect cannot contaminate a count. R2 and R4 need the phone.
- **INFO is enough.** Every line this round reads is `AppLog.i`. `log-level=2`.
- **The SSID no longer changes between bring-ups**, so the discard rule "a second `createGroup SUCCESS`" still applies per bring-up, but "the same `DIRECT-xx-` name twice" is the expected result, not a contamination.
- **The stand-down stays off** (`stand-down-station-for-wifi-direct` absent or `false`). It is the `five-ghz-channel` round 2's question, not this one's.
- **Two new settings keys**, both read at the next create, neither needs a relaunch to take:

| Key | Type | Meaning |
|---|---|---|
| `wifi-direct-stable-identity` | boolean, default `true` (absent = on) | Keep the name and passphrase. `false` is the 3.2 behaviour: a new temporary network every create |
| `wifi-direct-group-name` / `wifi-direct-group-passphrase` | string | The kept pair. Written by the app on the first bring-up; delete both to make it draw a new pair |
| `wifi-direct-last-group-ssid` / `wifi-direct-last-group-bssid` | string | What the last group looked like. Written by the app; delete both to reset the stability measurement |

- **`shared_prefs/` is root-owned on this rig**, so the app's own writes of the four keys above never reach disk (see the `rig-shared-prefs` note in §7a if present, and `set_hu_prefs.sh`). That matters here more than in any previous round, because two of the branch's features *are* those writes. §3 says what to do about it.

---

## 3. Preparation

### Step 0: a clean phone

Forget the head unit in the phone's Android Auto settings. On R2's first connect confirm:

```
GH.WIRELESS.SETUP: No WPP on TCP configuration found in storage for the head unit
```

If the phone logs `Trying to start WPP on TCP with configuration` anywhere before R4, it is carrying a record from an earlier thread; force-stop Gearhead and start the run again.

### The root-owned prefs problem, and the workaround

The candidate writes the kept pair on the first bring-up and the observed group on every bring-up. On this rig those writes fail silently, so left alone every bring-up would look like the first: `no kept network yet ... drawn now`, and `stable=` could never move off `unproven`.

Run R1's first bring-up, read the pair the app drew from the log (`group identity: no kept network yet, so DIRECT-XX-Navegadortz2 is drawn now`) and the passphrase from `dumpsys wifip2p` (it prints the group's `passphrase:`), and **write both keys yourself** with `set_hu_prefs.sh` before the second bring-up, plus the observed pair from the first read-back line:

```xml
<string name="wifi-direct-group-name">DIRECT-XX-Navegadortz2</string>
<string name="wifi-direct-group-passphrase">the12chars</string>
<string name="wifi-direct-last-group-ssid">DIRECT-XX-Navegadortz2</string>
<string name="wifi-direct-last-group-bssid">XX:XX:XX:XX:XX:XX</string>
```

From bring-up 2 on the app reads them and the round measures what it was written to measure. After every later bring-up, update `wifi-direct-last-group-bssid` to the BSSID the read-back line printed, so each comparison is against the previous group and not the first. Say in Setup notes that this was done by hand and why. If `dumpsys wifip2p` does not print the passphrase on this ROM, take it from the `Credentials updated. SSID=...` line by adding `log-level=1` for bring-up 1 only; it is not printed at INFO.

### Settings

Written with the app stopped, read back before the first run counts.

**Every WiFi Direct run (R1 to R4, R6):**

```xml
<int name="wifi-connection-mode" value="3" />
<int name="native-ap-transport" value="0" />
<int name="log-level" value="2" />
<boolean name="native-wifi-version-exchange" value="false" />
<string name="hotspot-ssid"></string>
<string name="hotspot-password"></string>
<string name="static-bssid">0</string>
<string name="hotspot-interface"></string>
```

Band and channel as `five-ghz-channel` round 1's R1 wrote them for channel 36, so the group is one the phone lists. `static-bssid` must be `0` for R1 to R3: an override makes the stability verdict `yes` at once, which would hide the measurement.

R4 sets `native-wifi-version-exchange=true`. R6 sets `wifi-direct-stable-identity=false`.

Diff `settings.xml` against a fresh backup at the start and state the delta even if zero.

### Build gate

Both APKs, md5s recorded and different, and the candidate's identity checked by symbol:

```bash
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'P2pGroupIdentityPolicy'     # > 0
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'GroupIdentityStabilityPolicy' # > 0
unzip -p <baseline.apk>  'classes*.dex' | strings | grep -cF 'P2pGroupIdentityPolicy'     # 0
```

---

## 4. The lines that decide every run

Verified with `grep -F` against `652ba40f`. All INFO.

**The identity the create asked for.** One per bring-up, before `createGroup`:
```
WifiDirectManager: group identity: no kept network yet, so DIRECT-XX-Navegadortz2 is drawn now and kept for every later create.
WifiDirectManager: group identity: asking for the kept network DIRECT-XX-Navegadortz2 again, so a phone that saved it can rejoin without being set up for a new one.
WifiDirectManager: group identity: a new network on every create (DIRECT-XX-Navegadortz2), as the setting asks; the phone will be set up for it over Bluetooth.
```

**The read-back, the line the round is built on.** Once per group, and once more if the BSSID only became readable on a later callback:
```
WifiDirectManager: group identity ssid=DIRECT-XX-Navegadortz2 persistent=yes (netId 3) asked=persistent matchesRequest=yes bssid=AA:BB:CC:DD:EE:FF stable=unproven (first group under this name; the next one decides) source=IPv6 link-local
```
The fields, and what each answers:

| Field | Values | Answers |
|---|---|---|
| `persistent=` | `yes (netId N)` / `no (temporary)` | question 1: did the platform store the profile |
| `asked=` | `persistent` / `temporary` / `framework profile` | which create path ran |
| `matchesRequest=` | `yes` / `no` | did the platform rename or re-key the group |
| `stable=` | `yes` / `unproven` / `no` | question 2, from bring-up 2 on |

**The refresh no longer recreates.** Every ten seconds while the handshake waits, and once before each poke:
```
AapService: WiFi Direct credential refresh requested.
WifiDirectManager: refresh: the group DIRECT-XX-Navegadortz2 is up, so its credentials are read again rather than the group remade.
WifiDirectManager: refresh: a group was asked for 1234ms ago and has not answered yet, so nothing is remade underneath it.
WifiDirectManager: refresh: no group is up, so one is created.
```

**The verdict beside the credentials:**
```
WifiDirectManager: SUCCESS - Providing credentials to listener. SSID=..., IP=192.168.49.1, BSSID=..., identity stable=unproven
NativeAA: Credentials updated. SSID=..., IP=..., BSSID=..., identity stable=yes
```

**The endpoint decision (R4 only):**
```
NativeAA: advertising WPP over TCP at 192.168.49.1:5299
NativeAA: not advertising WPP over TCP: the WiFi Direct group's name and address have not yet been seen to repeat on this unit, ...
NativeAA: not advertising WPP over TCP: this unit gives its WiFi Direct group a new address on every create, ...
```

**Unchanged lines the counts use:** `createGroup SUCCESS`, `WirelessServer: Incoming connection detected`, `[TX] Wrote TYPE 3`, `NativeAA: Connection accepted from`, `Handshake: SSL handshake complete`, `p2p-wlan0-N`.

**Phone side (R2, R4):** `WIRELESS_WIFI_CACHED_CREDENTIALS_INVALID`, `No WPP on TCP configuration found in storage`, `Trying to start WPP on TCP with configuration`, `Connecting to the WiFi network for WPP`, `NETWORK_NOT_FOUND`. Capture the phone with `stdbuf -oL adb -s <phone> logcat -v time` alongside, and grep with `-a`.

---

## 5. Runs

### R0: build gate and unit tests

Candidate **1169 / 0**, baseline **1135 / 0**. Named classes: `P2pGroupIdentityPolicyTest` (15), `NativeRefreshPolicyTest` (5), `GroupIdentityStabilityPolicyTest` (10), `WppEndpointPolicyTest` (10). A failure here stops the round.

### R1: identity across five bring-ups (candidate, head unit only)

Phone Bluetooth **off** throughout. Five times: `headunit://exit`, wait 3 s, `force-stop`, launch, wait 30 s, read `dumpsys wifip2p`. Between bring-ups 1 and 2 write the four keys as §3 says; after each later bring-up update the last-group BSSID key.

PASS when all of:
- the five `group identity ssid=` lines name the **same** SSID, and bring-ups 2 to 5 say `asking for the kept network`;
- `persistent=yes` and `matchesRequest=yes` on every line;
- `dumpsys wifip2p` shows the same `networkName` and `passphrase` five times;
- `createGroup SUCCESS` is 1 per bring-up.

Record separately, as the round's second answer: the five BSSIDs, and `stable=` on bring-ups 2 to 5. `stable=yes` from bring-up 2 on means this unit keeps the address; `stable=no` every time means it re-randomizes. Either is a result; it is not part of the PASS condition, and it decides R4.

If `persistent=no (temporary)` appears with `asked=persistent`: FAIL, keep the capture, and R2 to R4 become INCONCLUSIVE on this unit (the platform ignored the request; the SSID may still repeat because the name is ours, note whether it does).

### R1b: the baseline, two bring-ups

Same procedure on the parent. Expect two different `DIRECT-xx-` names and no `group identity` lines at all (the line does not exist there). Two is enough; the behaviour is documented.

### R2: reconnect time, A/B (phone needed)

Phone Bluetooth on, head unit launched first and settled. For each arm, three phone-driven reconnects by cycling the phone's Bluetooth (§7a's method): for each, measure `[TX] Wrote TYPE 3` to `WirelessServer: Incoming connection detected`, and count `WIRELESS_WIFI_CACHED_CREDENTIALS_INVALID` on the phone.

PASS when the candidate's three reconnects each form a session, and the phone logs `WIRELESS_WIFI_CACHED_CREDENTIALS_INVALID` on the **first** candidate connect at most and not on the later ones. Report the six durations as numbers; a candidate that is not faster is a finding, not a FAIL, because the association time is the phone's.

Keep the head unit up across the three reconnects (do not exit between them). The point is that each reconnect lands on a recreated group under the same name.

### R3: no churn while nobody joins, A/B (head unit only)

Phone Bluetooth off. Launch, wait **75 s**, exit. Count in the capture:

```bash
grep -ac "createGroup SUCCESS" rN.txt
grep -ac "refresh: the group" rN.txt          # candidate only
grep -ac "refresh: a group was asked for" rN.txt
grep -a -o "p2p-wlan0-[0-9]*" rN.txt | sort -u | wc -l
```

PASS when the candidate shows `createGroup SUCCESS` = 1 in the first 60 s (the join watchdog may add one at 60 s; say if it did), at least one `refresh: the group ... is up` line, and one `p2p-wlan0-N`. The baseline is expected to show one `createGroup SUCCESS` per ten seconds and a climbing interface index; report both counts.

### R4: WPP over TCP on WiFi Direct (candidate, phone needed)

**Only if R1 reported `stable=yes`.** Otherwise INCONCLUSIVE by design: run one connect with `native-wifi-version-exchange=true` anyway, and PASS-by-refusal when the log says `not advertising WPP over TCP: this unit gives its WiFi Direct group a new address on every create` and the session still forms over Bluetooth. Then go to R4b.

With `stable=yes`: `native-wifi-version-exchange=true`, phone forgotten and clean (step 0). Then:

1. First connect: expect `NativeAA: advertising WPP over TCP at 192.168.49.1:5299` and a normal session.
2. Phone-driven reconnect (phone Bluetooth cycle), head unit left up: expect a session with `WppTcpServer:` lines, **zero** new `NativeAA: Connection accepted from` for that reconnect, and the phone logging `Trying to start WPP on TCP with configuration`.
3. `headunit://exit`, relaunch (new group, same name), phone reconnect again: expect the same as step 2. This is the step that proves the record survived a group recreate.
4. Finally `headunit://exit`, set `native-wifi-version-exchange=false`, relaunch, reconnect: the phone dials the stored endpoint anyway (it keeps it); a session must still form.

PASS when steps 1 to 3 hold. If step 3 fails with the phone looping `NETWORK_NOT_FOUND` / `Restarting WPP over TCP`, that is a FAIL that says the phone pins something the read-back did not see; keep both captures and forget the head unit on the phone before anything else runs.

### R4b: does the phone pin the BSSID at all? (optional, candidate, phone needed)

Only when R1 said `stable=no`. Set `static-bssid` to the BSSID the **current** group printed, so the verdict becomes `yes (the static BSSID setting fixes the address)` and the endpoint goes out; connect once; then exit and relaunch so the group comes up with a new BSSID while the phone holds the old one; reconnect. If the phone joins anyway, Gearhead matches on SSID alone and the branch's gate can be relaxed to name-only in a follow-up. If it loops `NETWORK_NOT_FOUND`, the gate is right as written. **Forget the head unit on the phone afterwards** either way, and clear `static-bssid` back to `0`.

### R6: the off switch (candidate, head unit only)

`wifi-direct-stable-identity=false`, two bring-ups. PASS when both `group identity:` lines say `a new network on every create`, the two SSIDs differ, and the read-back says `asked=temporary`. Then restore the key.

### R5, not runnable here

The Helper path (mode 2, WiFi Direct strategy) is untouched by the diff and cannot be run on this rig for the Gearhead 17.4 reason recorded in earlier rounds. Coverage is the diff itself: `checkGroupAndCreate` has no hunk.

---

## 6. Stop conditions

- R0 fails: stop.
- R1 FAIL with `persistent=no`: run R1b and R6, mark R2 to R4 INCONCLUSIVE, stop.
- R4 step 3 FAIL: forget the head unit on the phone, then continue with R6.

Leave the candidate installed, `settings.xml` restored to the round's baseline, `static-bssid` at `0`, the phone's head-unit record forgotten, and both phones' radios on.
