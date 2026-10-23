# five-ghz-channel, round 1: does a pinned channel reach the radio?

**Candidate:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `6f1ee214` (6 commits on `origin/main` @ `a7076ff4`), `3.3.0`
**Baseline:** none. Every run is candidate-only; the comparison is between channels, not between builds.

```bash
git fetch fork && git checkout feat/native-aa-wpp-tcp-and-hfp-link   # @ 6f1ee214
```

The branch was rebased onto `origin/main` and rewritten on 2026-09-01. Any older local ref for this work is orphaned; fetch and reset rather than pulling.

Read `TESTING-TEMPLATE.md` first, and §7a before planning. This brief carries only what is specific to the branch.

---

## 1. Why this round exists

Asking Android for "the 5 GHz band" does not name a channel. `wpa_supplicant` answers it by picking a random start index into eight candidates — 5180, 5200, 5220, 5240, 5745, 5765, 5785, 5805 — and taking the first supported one. Four of the eight are UNII-3, which several regulatory domains forbid a phone from joining, so on such a phone roughly half of all bring-ups produce a group it never lists in a scan at all. Not a weak connection: an invisible one.

The branch adds `wifi-5ghz-channel`, one setting shared by both transports, offering 36, 40, 44, 48 and 149. What each transport can do with it is very unequal, and this round measures that difference rather than assuming it:

- **WiFi Direct on API 29+** asks through `WifiP2pConfig.Builder.setGroupOperatingFrequency`, which is public SDK and reaches `wpas_p2p_group_add(freq=)` unchanged. This should work, and R1 is where that is either shown or not.
- **The access point** asks through `setSoftApConfiguration`, which is gated on signature permissions. This unit already refuses that call, so R3's pass condition is that the log says the request was refused rather than claiming a band it did not get.

A forced frequency is forced all the way down: `wpas_p2p_init_go_params` fails the group rather than choosing elsewhere when the driver or the regulatory domain refuses the channel. So a channel this unit will not host costs the group, not just the channel, and the retry ladder exists to spend its budget on the channel, then drop the channel and keep the band. R1 exercises both outcomes without having to arrange either.

**What this rig cannot test.** The pre-Q half of the change — the widened `setWifiP2pChannels` ladder, which used to offer only 36 or 149 through a boolean — needs a device below API 29, and §7a already records that ladder as unreachable on this Android 14 unit. Report it as untested, not as covered.

R4 picks up a second unproven lever on this branch at no extra rig time.

---

## 2. Rig facts that shape the runs

- **This unit's own WiFi is permanently associated to `Pegue Cdesta` on 5500 MHz** (§7a), and changing that has never been authorised. Every run below is therefore a group owner brought up on a radio that is already a station somewhere else. That is the normal state for this rig and is not a confound to remove; it is the condition the setting has to work under.
- **`p2p-bringup-loop-round1-results.md` measured groups coming up on 5180 MHz on this unit** with `wifi-direct-band` at both 0 and 1. So 5180 is where this driver already lands by itself. **Channel 36 therefore proves nothing on its own** — see R1, which is built around that.
- **The driver's 5 GHz list for this unit, country CO, is 36, 40, 44, 48, 149, 153, 157, 161, 165.** All five offered channels are in it, so a refusal is the driver or the group-owner path saying no, not the channel being unknown.
- **`WifiScanner` is broken on this unit by any route** (§7a). Every scan in this brief runs on the phone, never on the head unit.
- **This unit refuses `setSoftApConfiguration()` and can read `getSoftApConfiguration()`.** `wlan2` is the AP interface, `seth_lte0` the modem bridge. `wpp-over-tcp-round3-results.md` used `cmd wifi force-softap-band enabled 5` and `force-softap-channel` as the out-of-band substitute; R3 uses the same lever to separate "the app's request was refused" from "this unit cannot host 5 GHz at all".
- **Groups here log `5GHz createGroup SUCCESS!`, never `Standard createGroup SUCCESS!`.** Grep `createGroup SUCCESS` only.
- **A group is never reused.** Every bring-up tears the group down and makes a new one, so each run mints a fresh SSID and a fresh interface index. `p2p-wlan0-N` climbing across the round is expected, not a leak.
- **`shared_prefs/` is root-owned**, so the app's own writes never reach disk. Everything this round sets is a root write and is unaffected, but do not verify a value by force-stopping and expecting the app to have written anything back.
- **Never leave a `settings.xml.bak` beside the file.** SharedPreferences reads a stray `.bak` as an aborted write and restores it over the edit, silently.
- **Bring the head unit up before the phone**, or two sessions race.
- Use **`set_hu_prefs.sh`** for the settings changes: it writes several keys in one pass with one relaunch. Not `set_hu_pref.sh`, which relaunches per call and reinstalls whatever APK is newest.
- **`build_hur.sh` deletes the previous APK.** Copy each build out of `apks/` immediately.

---

## 3. Devices

| Role | Device | Notes |
|---|---|---|
| **D-HU** | UNISOC MT50, Android 14, BT `11:46:03:10:33:59` | head unit under test, wireless mode 3, every run |
| **D-POCO** | POCO X3 NFC, Android 12, Gearhead `17.5.663204`, BT `DC:B7:2E:5E:4E:59` | projecting phone; R2 and R4, and every scan in this brief |

R1 and R3 need no phone at all. Run them first: they are the fastest arms and R1 is the round.

---

## 4. Settings

`log-level=1` is DEBUG. Every line this round reads is an unguarded `AppLog.i` or `AppLog.w`, so DEBUG carries all of them and VERBOSE only costs ring buffer.

| Key | R1 | R2 | R3 | R4 |
|---|---|---|---|---|
| `wifi-connection-mode` | 3 | 3 | 3 | 3 |
| `native-ap-transport` | 0 | 0 | **1** | **1** |
| `wifi-direct-band` | **1** | **1** | n/a | n/a |
| `hotspot-band` | n/a | n/a | **1** | 1 |
| **`wifi-5ghz-channel`** | **0, 36, 40, 44, 48, 149** | **the two R1 proved, see R2** | **36** | 36 |
| `static-bssid` | 0 | 0 | 0 | 0 |
| `log-level` | 1 | 1 | 1 | 1 |
| `native-wifi-version-exchange` | false | false | false | false |

**`wifi-direct-band=1` is deliberate.** That is 5 GHz only rather than automatic, so a 2.4 GHz fallback can never quietly rescue a run and make a refused channel look like a success. It also makes the ladder legible: with a pinned channel it retries, then drops the channel and keeps the band, and only then gives up.

`native-wifi-version-exchange` stays false throughout, including R4. The QR carries the endpoint itself and the TCP server listens either way.

---

## 5. Log lines, all verified against `6f1ee214`

Head unit, `OPENHU`. The request:

```
WifiDirectManager: 5 GHz channel is channel 36 (5180 MHz), asked for as a fixed 5180 MHz.
WifiDirectManager: 5 GHz channel is automatic, so the driver picks within the band.
WifiDirectManager: Requesting Native AA P2P group on 5GHz band. Chosen by the user.
```

The result, which is the measurement — request and outcome on one line:

```
WifiDirectManager: onGroupInfoAvailable: SSID: ..., Freq: 5180 MHz (5GHz), 5180 MHz was asked for
```

The ladder, if this unit refuses a channel:

```
WifiDirectManager: 5GHz createGroup retries exhausted (...). This unit will not host a group on that channel, so the request goes back to the 5GHz band and the driver picks the channel.
WifiDirectManager: 5 GHz channel is channel 36 (5180 MHz), already refused by this unit, so the band decides.
```

Hotspot, R3:

```
SoftApConfigCompat: requesting 5 GHz channel 36 for the access point.
SoftApConfigCompat: requested 5 GHz, channel 36 via WifiConfiguration.apBand. Success=...
SoftApConfigCompat: could not configure the access point (...)
HotspotManager: Hotspot is up, but this device refused the request for 5 GHz ...
HotspotManager: Hotspot is up, and this device accepted the request for 5 GHz.
```

R4's gate, which prints whether or not the endpoint is advertised:

```
WppTcpServer: listening for Android Auto on TCP 5299
```

**Frequency reads outside the app.** The app cannot read an access point's frequency at all; for the P2P group its own line above is primary. Cross-check with:

```bash
adb -s <D-HU>   shell dumpsys wifip2p | grep -i "freq\|channel"      # the group
adb -s <D-HU>   shell dumpsys wifi    | grep -i "SoftApInfo"         # the access point, R3
adb -s <D-POCO> shell cmd wifi start-scan
adb -s <D-POCO> shell cmd wifi list-scan-results | grep -i "<the SSID>"
```

The last is the only reading that answers the question the setting exists for, because it is the phone's own radio reporting what it can see. Scans never run on D-HU.

---

## 6. Runs

### R0: build gate

`build_hur.sh`, then `run_unit_tests.sh`. Report **test count and failures**, measured not computed.

- Expected **1113 / 0**.
- Per class, measured on this tip: `FiveGhzChannelPolicyTest` **7**, `WifiP2pOperatingChannelPolicyTest` **22**,
  `NativeGroupBandPolicyTest` **26**, `SoftApBandPolicyTest` **12**, `ProjectionQrPolicyTest` **9**.
- Confirm the APK is the candidate by symbol rather than by version string — a resource or string grep has passed on the wrong build twice:
  ```bash
  unzip -p <apk> classes*.dex | strings | grep -c "FiveGhzChannelPolicy"   # non-zero
  unzip -p <apk> classes*.dex | strings | grep -c "ProjectionQrPolicy"     # non-zero, R4's marker
  ```
- `adb install -r -d`, and record the APK md5.

A failure here stops the round.

### R1: the channel matrix. **This is the round.**

D-HU only, no phone. Six runs, one per value of `wifi-5ghz-channel`: **0 (automatic), 36, 40, 44, 48, 149**. For each: write the settings with the app stopped, start the capture, launch, let the group settle until `onGroupInfoAvailable` has printed, stop.

One row per run:

| channel set | frequency asked for | frequency arrived | ladder fired? | group formed? |

Expected frequencies: **36 → 5180, 40 → 5200, 44 → 5220, 48 → 5240, 149 → 5745.**

**Read the matrix this way, and not as five independent passes.** This driver was already measured landing on 5180 by itself, so channel 36 arriving at 5180 is consistent with the pin working *and* with the pin being ignored. It discriminates nothing. **The runs that decide this round are 40, 44, 48 and 149.** If any one of them arrives on the frequency it named, the request reaches the radio. If all four arrive at 5180 while the log says a different frequency was asked for, the request is being ignored and the setting is decoration — that is a **FAIL**, and it sends this back to the `setGroupOperatingFrequency` call rather than to the policy.

**A refusal is also a pass, and a more interesting one**, provided all three hold: the ladder line prints, the pinned channel is dropped rather than retried forever, and a group still forms on the band afterwards. Record which channels this unit refuses; that is a hardware fact worth keeping whatever the verdict.

The automatic run is the control for the log rather than for the frequency: it must print `automatic, so the driver picks within the band` and carry **no** `was asked for` suffix.

Run the matrix once. Repeat only a channel whose result is ambiguous.

### R2: can the phone actually join it?

D-HU plus D-POCO, two runs, each a full session. Use **the two channels R1 showed to be genuinely different frequencies** — the intended pair is 36 and 149, but if R1 shows this unit refusing 149, substitute the highest channel it did host and say so.

For each run report: whether D-POCO listed the SSID in a scan, whether `WirelessServer: Incoming connection detected` appears, and whether the session reached `SSL handshake complete`.

Both channels joining is the expected result and is a PASS. **One joining and the other not is the finding this whole setting exists for** — it means this phone's regulatory domain refuses that range, and it makes the setting load-bearing rather than a convenience. Report which, with D-POCO's country code if it can be read:

```bash
adb -s <D-POCO> shell dumpsys wifi | grep -i "country"
```

Neither channel joining points at something other than the channel; check the BSSID line and the credentials before concluding anything about the band.

### R3: the access point, where the request is expected to be refused

D-HU, `native-ap-transport=1`, `wifi-5ghz-channel=36`, hotspot on, no phone except for the scan.

This arm is not expected to deliver channel 36, and that is not a FAIL. What it tests is whether the app tells the truth about it. **PASS requires both:**

1. The channel request is logged: `requesting 5 GHz channel 36 for the access point`, or the API-30+ equivalent.
2. What happened next is reported honestly — `accepted the request for 5 GHz` or `refused the request for 5 GHz` — and it matches what actually came up, read from D-POCO's scan results.

**FAIL** is the log claiming a band or channel the scan contradicts. That is the exact failure the honest-line rewrite was meant to end, and it would mean it did not.

Then one control, to separate the app's refusal from the hardware's: bring the AP up out of band with `cmd wifi force-softap-band enabled 5` and `force-softap-channel`, as `wpp-over-tcp-round3-results.md` did, and report the frequency that produces. If the unit will host 36 when the shell asks and not when the app asks, the limit is the permission and not the radio, which is worth stating plainly in the results.

### R4: is the QR provisioning route open at all? (free, if the rig is up)

The branch added a setup QR carrying Android Auto's own provisioning URL. Whether the phone accepts one is gated on Gearhead's `DEEP_LINK_ENABLED` experiment, whose default nobody has established, and **that question can be answered with no build and no UI**, from R3's own rig state.

Build the URL by hand from the credentials the round already has, and fire it at D-POCO:

```python
import base64
def s(n,v): b=v.encode(); return bytes([n<<3|2])+bytes([len(b)])+b
def v(n,x):
    out=bytes([n<<3|0])
    while True:
        b=x&0x7F; x>>=7; out+=bytes([b|(0x80 if x else 0)])
        if not x: return out
p = (s(1,"<the AP's SSID>")+s(2,"<its bssid, lower case>")+s(3,"<its passphrase>")+
     s(4,"<D-HU's IP on that network>")+v(5,5299)+s(6,"11:46:03:10:33:59")+v(7,8))
print("https://androidauto.com/projection/setup?data="+base64.urlsafe_b64encode(p).decode().rstrip("="))
```

SSID, BSSID and IP all come from `SoftApCredentials: SUCCESS - Providing credentials from ...` in R3's own capture. The passphrase is the unit's hotspot password. Port 5299 is what `WppTcpServer: listening for Android Auto on TCP 5299` says; use the port from the log if it differs.

```bash
adb -s <D-POCO> shell am start -a android.intent.action.VIEW -d "<the url>"
```

Report three things:

1. Whether Gearhead's `DeepLinkResolver` handled it at all, or the intent fell through to a browser. Falling through means the experiment is dark on this phone and the feature is inert there, which is the answer either way.
2. If it was handled: what confirmation it showed, and whether one tap wrote the record.
3. Whether a projection session then formed **with no Bluetooth accept** — no `Connection accepted from` on the head unit before `Incoming connection detected`. That is the whole point of the route and has never been observed on this rig.

Anything short of a session is not a FAIL for this round. The verdict sought is only whether the route is open.

### R5: harvest, and one paste that needs no extra run

Per-capture counts across R1 to R3:

```bash
grep -c "createGroup SUCCESS"     <captures>
grep -c "was asked for"           <captures>
grep -o "p2p-wlan0-[0-9]*"        <captures> | sort -u
grep -A 12 "BSSID source dump"    <one R1 capture>
```

The BSSID dump is a passenger. This branch also rewrote what the app says when no source names an address, and one paste of that block from any R1 run is worth keeping. If the `IPv6 link-local` row answers while the sysfs and hardware-address rows are null, say so: that is the rung the text was rewritten around.

Do not re-run anything for R5. If a capture was not kept, say so and skip it.

---

## 7. Not a verdict, but worth a line

Two observations, if the settings screen happens to be open at the end. House rules say the UI is never driven for setup, so neither is a pass condition and neither is worth a run of its own.

- The **5 GHz channel** row renders under the Native AA block and disappears when the band setting is 2.4 GHz only. Does it read sensibly beside the band row?
- The **Set up by QR code** row renders only on the hotspot transport. On a stopped service it should refuse with a message about starting the connection first. The refusal prints as `ProjectionSetupQr: no setup QR to show (...)` with the reason in brackets, and **which reason it names on a real head unit is worth having**: `NO_BLUETOOTH_IDENTITY` would mean this unit's own Bluetooth address is masked to the app, and the QR can never be drawn there without an override the branch deliberately does not offer.

---

## 8. Report back

`five-ghz-channel-round1-results.md` on this branch, in the template's §7 format, with verdicts from the four words — PASS, FAIL, INCONCLUSIVE, UNTESTABLE — and every measurement as a number, not an adjective: `5180 MHz`, not "5 GHz".

The numbers that matter:

1. **R1's table**, six rows: what was asked for and what arrived. This is the round.
2. **Which channels, if any, this unit refuses**, and whether the ladder recovered a group each time.
3. **R2's two sessions**: did both channels form one, and if not, which did not, and what D-POCO's scan saw.
4. **R3's honesty check**: what the log claimed, what the scan says, and what the out-of-band lever produced.
5. **R4's three answers**, if the arm was run.

**What a PASS would look like if the change did nothing:** every run forming a healthy group, every log line naming the channel the user picked, and every group arriving on 5180 MHz — because that is where this driver already goes. That is why 40, 44, 48 and 149 decide this round and 36 does not.
