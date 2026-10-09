# five-ghz-channel, round 2: does the pin survive a head unit that is joined to a network?

**Candidate:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `6f1ee214` (6 commits on `origin/main` @ `a7076ff4`), `3.3.0`
**Baseline:** none. Candidate-only, same tip as round 1. The comparison is between station states.

```bash
git fetch fork && git checkout feat/native-aa-wpp-tcp-and-hfp-link   # @ 6f1ee214, unchanged since round 1
```

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific
to the branch.

---

## 1. Why this round exists

Round 1 answered its own question completely: five pinned channels, five named frequencies, and an
automatic control that rolled onto 5805 MHz. `setGroupOperatingFrequency` reaches the radio. Nothing
about that needs re-running.

What round 1 did not measure is the one state in which a pinned frequency is known to be dangerous.
`wpas_p2p_setup_freqs()` computes how many channels are unused as `num_multichan_concurrent` minus
the channels already in use. On a single-radio unit that is 1, so an **associated station makes it
zero**, and from there a *requested* frequency that is not the station's own channel returns `-2` and
`createGroup` fails outright, logging `Cannot start P2P group on %u MHz as there are no available
channels`. With no frequency requested the same code instead hands the station's channel back as
`force_freq` and the group is quietly forced onto the home network's channel. So the two states are
not variations on one behaviour: asking for a channel is the branch that can fail.

Every round-1 run appears to have been made with the station down, which is the opposite of this
rig's documented normal state:

- All six R1 `BSSID source dump` blocks enumerate `dummy0`, `seth_lte0`, `lo` and `p2p-wlan0-N`.
  **No `wlan0` appears in any of them.**
- `p2p-bringup-loop-round1-results.md` recorded that this unit "dropped its `Pegue Cdesta` station
  association during R3's `svc wifi` cycles and had not re-joined by round end", and asked the next
  round to re-verify. Nothing since has.
- Round 1's brief asserted the association from §7a instead of reading it, which is exactly what §7a
  says not to do.

So the retry ladder that exists for the `-2` case has never run on hardware: `createGroup SUCCESS`
was 1 on the first attempt in all eight round-1 bring-ups and the ladder fired zero times. That is
the whole of the remaining shipping question for `cc85d7ab`, and one afternoon answers it.

**What the ladder does, so the log is readable.** Four retries at 2 s apart on the pinned frequency
(`createGroup failed (...), removing group and retrying in 2s (retry N/4)`), so five attempts and
about eight seconds; then the channel is dropped and the band-only budget starts over
(`createGroup retries exhausted (...). This unit will not host a group on that channel, so the
request goes back to the 5GHz band`); then the standard fallback, or nothing at all if the band
preference is 5 GHz only. A later bring-up in the same process says `already refused by this unit, so
the band decides` instead of naming a frequency.

---

## 2. Rig facts that shape the runs

- **Verify the station state before writing anything down**, per §7a, and record it per run:

  ```bash
  adb shell dumpsys wifi | grep -iE "mWifiInfo|SSID|Frequency" | head
  ```

- **If the unit is not joined, re-join it.** §7a records `Pegue Cdesta` at 5500 MHz as this rig's
  permanent state, so restoring it is a repair, not the change of association that has never been
  authorised. §7a's own nudge is the lever:
  `adb shell cmd wifi connect-network "Pegue Cdesta" wpa2 "<psk>"`, then re-read `dumpsys wifi` and
  confirm `mWifiInfo` names the SSID and a frequency before launching anything. If it will not
  re-join, this round is **UNTESTABLE**: say so and stop, rather than repeating round 1 unjoined.
- **5500 MHz is DFS and is not one of the five offered channels**, so every run below asks for a
  frequency the station is not on. That is the point: it is the condition that produces `-2`.
- **`svc wifi enable` does not reliably bring the station back** after a disable (§7a). Nothing here
  needs WiFi disabled; do not disable it.
- **A group is never reused**, so `p2p-wlan0-N` climbing across the round is expected.
- **`shared_prefs/` is root-owned**; every settings write is a root write, and the app never writes
  back. Never leave a `settings.xml.bak`.
- **Bring the head unit up before the phone.**
- Use **`set_hu_prefs.sh`** (plural), and round 1's **`five_ghz_matrix.sh`**, which already does the
  per-channel bring-up and the request/outcome greps this round needs, and only wants the station
  read adding.

**Two corrections to round 1's own results file**, both harmless there and worth not repeating:

- Its unit line reads "permanently associated to `Pegue Cdesta` / `Navegadortz2` on 5 GHz".
  `Navegadortz2` is **this unit's own device name**. The P2P SSIDs are minted from it
  (`DIRECT-XX-Navegadortz2`), `Connection accepted from ... on local radio [Navegadortz2]` names it,
  and the SoftAP in the `wpp-over-tcp` round carried it too. It is not a network the unit joins.
- D-POCO is **Android 15**, not Android 12.

---

## 3. Devices

| Role | Device | Notes |
|---|---|---|
| **D-HU** | UNISOC MT50, Android 14, BT `11:46:03:10:33:59` | head unit under test, wireless mode 3, every run |
| **D-POCO** | POCO X3 NFC, **Android 15**, Gearhead `17.5.663204`, BT `DC:B7:2E:5E:4E:59` | projecting phone; R3 only |

R1 and R2 need no phone. Run them first.

---

## 4. Settings

Identical to round 1's R1 except where a run says otherwise. `log-level=1` is DEBUG, which carries
every line this round reads.

| Key | R1 | R2 | R3 |
|---|---|---|---|
| `wifi-connection-mode` | 3 | 3 | 3 |
| `native-ap-transport` | 0 | 0 | 0 |
| `wifi-direct-band` | **1** | **1** | **1** |
| **`wifi-5ghz-channel`** | **36, 149, 0** | the channel R1 made fail, or 36 | whichever R1 settled on |
| `static-bssid` | 0 | 0 | 0 |
| `log-level` | 1 | 1 | 1 |
| `native-wifi-version-exchange` | false | false | false |

`wifi-direct-band=1` is 5 GHz only, again deliberately: a 2.4 GHz rescue would hide a refused
channel behind a healthy-looking group. It also means the `GIVE_UP` rung is reachable, and reaching
it is a real result, not a broken run.

---

## 5. Log lines, all verified against `6f1ee214`

The request, and the result on one line:

```
WifiDirectManager: 5 GHz channel is channel 36 (5180 MHz), asked for as a fixed 5180 MHz.
WifiDirectManager: onGroupInfoAvailable: SSID: ..., Freq: 5180 MHz (5GHz), 5180 MHz was asked for
```

The ladder, none of which has ever printed on hardware:

```
WifiDirectManager: 5GHz channel 36 (5180 MHz) createGroup failed (...), removing group and retrying in 2s (retry 1/4)...
WifiDirectManager: 5GHz channel 36 (5180 MHz) createGroup retries exhausted (...). This unit will not host a group on that channel, so the request goes back to the 5GHz band and the driver picks the channel.
WifiDirectManager: 5 GHz channel is channel 36 (5180 MHz), already refused by this unit, so the band decides.
WifiDirectManager: 5GHz createGroup retries exhausted (...) and the band is set to 5 GHz only, so no group is created.
```

Outside the app:

```bash
adb -s <D-HU> shell dumpsys wifi   | grep -iE "mWifiInfo|SSID|Frequency" | head   # the station, before every launch
adb -s <D-HU> shell dumpsys wifip2p | grep -i "freq\|channel"                     # the group
adb -s <D-POCO> shell cmd wifi start-scan
adb -s <D-POCO> shell cmd wifi list-scan-results | grep -i "<the SSID>"
```

Scans never run on D-HU, because `WifiScanner` is broken there by any route (§7a).

---

## 6. Runs

### R0: identity gate, not a build

`6f1ee214` has not moved and round 1 left the candidate installed. Confirm rather than rebuild:

```bash
adb -s <D-HU> shell pm path com.andrerinas.headunitrevived      # then md5 the apk it names
```

- Expect md5 `5e1c871dcf7b70c46b02e43498a2a955`.
- If it matches, no build is needed and the unit tests do not need re-running; say so.
- If it does not, `build_hur.sh` + `run_unit_tests.sh`, expect **1113 / 0**, and confirm the APK by
  DEX symbol (`unzip -p <apk> classes*.dex | strings | grep -c "FiveGhzChannelPolicy"`, non-zero)
  rather than by version string.

### R1: the matrix, with the station up. **This is the round.**

D-HU only, no phone. Three runs: `wifi-5ghz-channel` = **36**, **149**, **0**. Before each launch,
read the station and record it. For each run, capture from before the launch until either
`onGroupInfoAvailable` has printed or the ladder has finished. Allow **60 s**, because a full ladder
is roughly eight seconds of retries and then a second budget.

One row per run:

| channel set | station SSID / freq at launch | createGroup first try? | ladder lines, and how many | launch → group (s) | frequency arrived |

The three outcomes, all of which are results, pre-registered so the round cannot be read backwards:

1. **The group forms on the pinned frequency while the station is up on 5500 MHz.** Then this chip
   carries more than one concurrent channel and the `-2` branch does not apply to it. **PASS**, and
   the ladder is still unexercised on hardware, so report it that way rather than as covered.
2. **`createGroup` fails, the ladder drops the channel, and a band-only group forms.** The ladder
   works, which is the design. **PASS.** The numbers that matter are how many attempts were spent and
   how many seconds passed from launch to the group that survived. Report the frequency the band-only
   group landed on: if it is 5500 MHz, that is the `force_freq` branch, and worth saying.
3. **`createGroup` fails and no group ever forms** (the `5 GHz only, so no group is created` line).
   **FAIL.** The pin then has to be gated on the station state before it ships, which is a code
   change and not a settings default.

The automatic run is the control for the same state: with no frequency requested there should be no
failure at all, and where it lands says whether this driver is being forced onto the station's
channel or is still free to roll.

Run each once. Repeat only a run whose outcome is ambiguous.

### R2: is the ladder's cost paid again on every bring-up?

**Only run this if R1 produced outcome 2 or 3.** `pinnedChannelAbandoned` is an in-memory field
cleared whenever the manager is torn down, so a force-stop and relaunch should spend the whole budget
again rather than remembering the refusal.

Two launches with the same settings as the R1 run that failed, `force-stop` between them. Report
launch → group for each, and whether the second launch printed `already refused by this unit` (it
should not, across a process restart) or went back to naming the frequency.

If the cost is real and repeated, that is a follow-up commit, not a blocker for this branch. Say so
plainly and give the seconds.

### R3: does a phone still join, with the station up?

One full session on whichever channel R1 settled on, D-POCO joining as in round 1's R2. Report
whether D-POCO listed the SSID in its own scan and at what frequency, and whether the session reached
`WirelessServer: Incoming connection detected` and `SSL handshake complete`.

Round 1 got two clean sessions with the station down. This is the same check in the state a user's
head unit is actually in. A session forming is a **PASS**; not forming needs the group's own
frequency and the scan before anything is concluded about the channel.

### R4: harvest

Per-capture counts across R1 to R3:

```bash
grep -c "createGroup SUCCESS"        <captures>
grep -c "createGroup failed"         <captures>
grep -c "retries exhausted"          <captures>
grep -c "was asked for"              <captures>
grep -c "MATCH! Starting AapService" <captures>
grep -o "p2p-wlan0-[0-9]*"           <captures> | sort -u
```

Plus the station read from before each launch, verbatim. Do not re-run anything for this.

---

## 7. What this rig still cannot answer

State each once, as UNTESTABLE, and do not spend a run on any of them:

- The **pre-API-29 `setWifiP2pChannels` ladder** needs a device below API 29. There is none here.
- The **access point's channel request** needs a unit that can read its own AP config through
  `getSoftApConfiguration()`. This one returns null, so the request is never made, as round 1's R3
  measured. Coverage stays on `SoftApBandPolicyTest`.
- The **domain split**, one channel joinable and another not, needs a phone whose regulatory domain
  forbids UNII-3. Both rig devices are country CO, which permits the whole 36 to 165 list.
- The **setup QR** is closed at Gearhead's own experiment gate on `17.5.663204`
  (`DEEP_LINK_ENABLED is disabled`), established in round 1's R4. Nothing to re-measure.

---

## 8. Report back

`five-ghz-channel-round2-results.md` on this branch, in the template's §7 format, verdicts from the
four words, every measurement a number.

The numbers that matter:

1. **The station state at every launch**, read from `dumpsys wifi`, not assumed. If the round ran
   unjoined again, that is the first line of the results and everything else is provisional.
2. **R1's three rows**: what was asked for, whether the first attempt succeeded, and what arrived.
3. **If the ladder fired**: how many attempts, how many seconds, and what the group that survived
   landed on.
4. **R2's two launches**, if it ran: the seconds each.
5. **R3**: session or no session, and the frequency D-POCO's own scan reported.
