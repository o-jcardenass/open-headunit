# PR readiness, round 1: three branches, two builds, everything that is still unproven

Three pull requests are about to open. This round covers what is left unverified on each. Two of
them share one APK, because one is stacked on the other.

| PR | Branch | Tip | Commits | Tests |
|---|---|---|---|---|
| 1 — Native AA wireless, WiFi Direct and hotspot | `fork/pr/native-aa-wireless` | `133e196d` | 14 on `main` | 1208 / 0 |
| 2 — Bluetooth auto-start and auto-disconnect | `fork/pr/bt-auto-start` | `99eb41a7` | 2 on PR 1 | 1223 / 0 |
| 3 — Automation command surface | `fork/pr/automation-command-surface` | `773e8e77` | 2 on `main` | 1010 / 0 |

**Build APK A from `pr/bt-auto-start`** (it contains PR 1 whole) and **APK B from
`pr/automation-command-surface`**. Two builds, two md5s, both recorded.

```bash
git fetch fork
git rev-parse fork/pr/bt-auto-start fork/pr/automation-command-surface
```

Read `TESTING-TEMPLATE.md` first, and §7a before planning.

> **These branches are a regrouping, not new work.** PR 1 and PR 2 together are byte-identical to
> the stack the last round tested (`git diff` against it is empty apart from a workflow file), and
> PR 3 is byte-identical to the branch it was rebuilt from. So everything the last round passed still
> holds, and only the runs below are open. Do not re-run the earlier briefs.

---

## 1. What is actually unproven

- **PR 1: one run.** The last round's R6 failed: with the station stand-down on, the bring-up made
  two P2P groups instead of one. That is fixed and needs confirming. Everything else on PR 1 passed
  on hardware.
- **PR 2: nothing new, one smoke run.** Its own round passed where the rig allowed. The run below
  only shows the regrouping did not break it.
- **PR 3: never run on hardware at all.** It is the bulk of this round.

---

## 2. Preparation

Settings as the previous wireless rounds, with the one difference that is the point of §4:

| Key | Value |
|---|---|
| `wifi-connection-mode` | `3` (Native AA) |
| `stand-down-station-for-wifi-direct` | **`true`** for §4, absent everywhere else |
| `wifi-5ghz-channel` | `36` |
| `static-bssid` | `0` |
| `native-wifi-version-exchange` | `false` |
| `log-level` | `1` (DEBUG — the enum is VERBOSE 0, DEBUG 1, INFO 2, so `2` hides every DEBUG line) |

D-HU **joined to its own WiFi network** before every §4 run, or the stand-down has nothing to stand
down and the run is void. D-POCO paired, Bluetooth on. Use `set_hu_settings_host.py`;
`set_hu_prefs.sh` still cannot write a `<set>`.

### Build gate (R0)

```bash
# APK A
unzip -p <A.apk> 'classes*.dex' | strings | grep -cF 'createClaimed'             # > 0
unzip -p <A.apk> 'classes*.dex' | strings | grep -cF 'CredentialFreshnessPolicy' # > 0
unzip -p <A.apk> 'classes*.dex' | strings | grep -cF 'BtAutoDisconnectPolicy'    # > 0
# APK B
unzip -p <B.apk> 'classes*.dex' | strings | grep -cF 'AutomationCommandPolicy'   # > 0
unzip -p <B.apk> 'classes*.dex' | strings | grep -cF 'AutomationOutputPolicy'    # > 0
```

Unit tests: A **1223 / 0**, B **1010 / 0**. A failure stops the round.

---

## 3. Runs

Numbered per PR. §4 is PR 1, §5 is PR 2, §6 is PR 3.

---

## 4. PR 1 — one bring-up chain through the station stand-down

**APK A.** `stand-down-station-for-wifi-direct=true`, D-HU joined to its WiFi network, app not
running. Launch the app, let it arm Native AA, capture until a session forms, then `headunit://exit`.
**Three iterations** — this is a timing race and one clean run proves little.

What failed last time, for reference:

```
:53.603  a Native AA group create is claimed (waiting for this unit to leave its own network)
:53.823  startNativeAaQuietHost() requested          <- chain 1, from the P2P-enabled receiver
:54.659  5GHz createGroup SUCCESS!
:55.040  StationStandDown: this unit has left its WiFi network.
:55.042  startNativeAaQuietHost() requested          <- chain 2, from the stand-down callback
:55.589  5GHz createGroup SUCCESS!
```

**PASS**, per iteration:

- exactly **one** `startNativeAaQuietHost() requested` between the claim line and `createGroup SUCCESS`;
- exactly **one** `5GHz createGroup SUCCESS!` at bring-up;
- the claim line `(waiting for this unit to leave its own network)` still appears and
  `StationStandDown: this unit has left its WiFi network.` still follows it — the fix must suppress
  the second chain, not the stand-down;
- a session forms: `WirelessServer: Incoming connection detected`, then `SSL handshake complete`;
- `p2p-wlan0-N` advances by **1**, not 2.

**FAIL** if two chains appear. Quote both `startNativeAaQuietHost` timestamps and the
`WIFI_P2P_STATE_CHANGED_ACTION state=` lines around them — those say which of the two started it.

Also from the same captures, no extra run: the line

```
WifiDirectManager: this unit's WiFi Direct reported N off/on state changes in the last 5s with no request of ours outstanding ...
```

must be **absent**, and no stack-cycled banner may appear on the main screen. The stand-down cycles
P2P by design and this build stamps it as ours; a warning here would be a false accusation.

If the stand-down instead logs `this unit is joined to a network the app is not allowed to name`,
that iteration does not exercise the window. Rejoin the network and repeat it.

## 5. PR 2 — the auto-disconnect still fires (smoke, one run)

**APK A**, D-POCO in Self Mode exactly as its own round: `connection-modes={self}`,
`auto-start-bt-macs` and `auto-disconnect-bt-macs` both `{<D-HU MAC>}`, `wifi-connection-mode=1`,
`kill-on-disconnect=false`, overlay op `allow`, head-unit server listening. Force the D-HU↔D-POCO
Bluetooth link up with a radio cycle on D-POCO first.

Session up and aged past 60 s, then turn D-HU's adapter off.

**PASS**: `Bluetooth auto-disconnect: <mac> went away; ending the session in 5000ms unless it comes
back.` then `stayed away; ending the session the way the Exit button does.`, the session ends, and
the adapter self-reverting produces `MATCH! Starting AapService` and a fresh session. One run is
enough; this is a regression check on a regrouping, not a new question.

## 6. PR 3 — the automation command surface

**APK B**, on D-HU. Nothing here needs a phone except R6.5. All commands go to one receiver:

```bash
PKG=com.andrerinas.headunitrevived
RX=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
A=com.andrerinas.openheadunit
```

`am broadcast` is ordered, so every reply comes back on the `data=` field of the command's own
output. Capture logcat throughout and keep every `data=` line in the results.

### 6.1 Build identity

```bash
adb shell am broadcast -n $RX -a $A.ACTION_QUERY_STATE
```

**PASS**: the reply is JSON and names the commit the APK was built from, and it matches
`773e8e77`. This is the whole point of the first commit — a log that cannot be tied to a build makes
a fix and a report of it failing look the same.

### 6.2 Control commands

With no session up, in order: `ACTION_START_SELF_MODE`, wait for the session, `ACTION_QUERY_STATE`,
`ACTION_RAISE_PROJECTION`, `ACTION_DISCONNECT`, `ACTION_QUERY_STATE`.

**PASS**: each is accepted, the session starts and ends, and the two state replies differ in the way
the run did. Record each reply verbatim.

### 6.3 Configuration is refused while the switch is off

*Allow external configuration* **off** (its default — confirm in Settings, do not assume).

```bash
adb shell am broadcast -n $RX -a $A.ACTION_GET_SETTINGS
adb shell am broadcast -n $RX -a $A.ACTION_SET_LOG_LEVEL --es level debug
```

**PASS**: both are refused and the reply says why. **FAIL** if either is carried out — `log-level`
must be unchanged afterwards, read back from `settings.xml`.

### 6.4 An export withholds credentials — the run that matters most

Turn *Allow external configuration* **on**. First make sure there is something to leak: set
`hotspot-password` and `auto-start-bt-macs` to known values through `settings.xml`, then

```bash
adb shell am broadcast -n $RX -a $A.ACTION_GET_SETTINGS
```

**PASS**: the reply contains **none** of `hotspot-password`, `hotspot-ssid`, `auto-start-wifi-ssid`,
`auto-start-bt-macs`, `auto-start-bt-name`, `static-bssid`, and carries a `withheld` count greater
than zero. Grep the reply for the literal values you set — zero hits.
**FAIL, and stop the round, if any value appears.** Any app on the device can send this command.

### 6.5 A write cannot choose where it lands

```bash
adb shell am broadcast -n $RX -a $A.ACTION_GET_SETTINGS --es path /data/data/$PKG/shared_prefs/settings.xml
adb shell am broadcast -n $RX -a $A.ACTION_GET_SETTINGS --es path /sdcard/Download/ohu-settings.json
```

**PASS**: the first is refused with a reason and `settings.xml` is untouched (compare md5 before and
after); the second succeeds and the file exists. The write runs with the app's own privileges, so an
unconstrained path would let any caller put chosen bytes into private storage.

### 6.6 The session broadcast reports the session

Watch it while running a real projection session, USB or wireless, whichever this rig forms fastest:

```bash
adb shell am broadcast -a com.andrerinas.headunitrevived.SESSION_STATE --receiver-foreground &
adb logcat -s OPENHU | grep --line-buffered "session state"
```

**PASS**: `state` reaches `projecting` while Android Auto is on screen, `transport` names the
transport actually used, and a `headunit://exit` produces `disconnected` with `reason=user_exit`.
**FAIL** if any broadcast carries a network credential or names the phone — grep the captured
extras for the SSID, the passphrase and the phone's Bluetooth name; all three must be absent.

### 6.7 The log marker

```bash
adb shell am broadcast -n $RX -a $A.ACTION_LOG_MARKER --es text "round1-check"
```

**PASS**: `AutomationMarker: round1-check` appears in the log at WARN. This is the tool that lets two
runs share one capture; if it works, use it to separate every run above.

---

## 7. Stop conditions and restore

- R0 fails on either APK: stop.
- **6.4 leaks a credential: stop the round and report immediately.** Nothing else in PR 3 matters
  until that is fixed.
- §4's stand-down refuses on all three attempts because the network is unnameable: report
  **UNTESTABLE** and quote the line. Turning Location on usually makes the network readable.

Restore: *Allow external configuration* back **off**, `stand-down-station-for-wifi-direct` back to
absent, both `settings.xml` restored and verified byte-identical, radios on, D-HU rejoined to its
WiFi network, any file written in 6.5 deleted.

---

## 8. Report back

Per PR, one line each: §4's two counts across three iterations; §5 pass or fail; and for PR 3,
which of 6.1 to 6.7 passed, with 6.4's grep result quoted in full either way. All three passing is
what opens the pull requests.
