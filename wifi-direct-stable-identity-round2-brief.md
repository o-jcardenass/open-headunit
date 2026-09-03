# wifi-direct-stable-identity, round 2: the give-up on a re-addressing unit, and the watchdog's fallback

**Candidate:** `fork/feat/wifi-direct-stable-identity` @ `f4c32678` (4 commits on `ef74866c`), `3.3.0`
**Baseline:** `fork/feat/native-aa-wpp-tcp-and-hfp-link` @ `ef74866c`, the parent, for R2 only. Round 1's baseline APK (`54584d347ffe9e4ba7d31149c541fa16`) is still valid.

```bash
git fetch fork
git rev-parse fork/feat/wifi-direct-stable-identity        # f4c32678...
```

| SHA | What |
|---|---|
| `a0940339`, `8dc5f92e`, `652ba40f` | Round 1's three commits, unchanged |
| `f4c32678` | A unit seen to re-address goes back to a fresh pair per create; the watchdog's no-band fallback keeps the identity; the device name stands in for a P2P name that has not arrived |

Read `TESTING-TEMPLATE.md` first, and §7a before planning. Round 1's Setup notes apply verbatim: root-owned prefs, the passphrase read from `/data/vendor/wifi/wpa/p2p_supplicant.conf`, and the reconnect trigger being `headunit://disconnect` plus a head-unit Bluetooth cycle. `wds_bringup.sh` and `wds_reconnect.sh` do the work again.

---

## 1. Why this round exists

Round 1 answered both of its questions. The platform honours the persistent request, and this unit gives the group a new BSSID on every create. It also found two things the fourth commit fixes, and both are measurable head-unit-only:

- **On a re-addressing unit a kept name was a cost.** Two of three reconnects took about ten seconds against the baseline's two, because the phone tried its saved network at the dead address first. The candidate now marks such a unit (`wifi-direct-group-address-moves`, sticky) the moment the comparison says `stable=no`, and from the next create on draws a fresh temporary pair, exactly as the off switch does, until the measurement is reset.
- **The join watchdog's fallback dropped the identity.** After two recreates it calls the no-band create, which used the two-argument overload and brought up the *older* `DIRECT-TQ-Navegadortz2` profile (`netId 0`) instead of the kept `DIRECT-WC`. On API 29 and above the fallback now asks for the identity with the band left to the platform, and only a refusal for a reason other than BUSY sends it back to the platform's profile.

Plus a small one: round 1's R6 first bring-up was named `DIRECT-PB-HeadUnit` because the P2P device name had not arrived yet. The unit's own device name now stands in.

---

## 2. What is different about this round

- **One new key**, written by the app when it sees `stable=no` with a readable address, and on this rig therefore written by hand:

| Key | Type | Meaning |
|---|---|---|
| `wifi-direct-group-address-moves` | boolean, default `false` | This unit has been seen to re-address the group. While `true`, a fresh temporary pair per create although `wifi-direct-stable-identity` is on |

- **The warning line the app prints when it decides that**, once per unit (it checks the flag first, so with root-owned prefs it prints on every bring-up that says `stable=no`):
```
WifiDirectManager: this unit gives its WiFi Direct group a new address on every create, so the kept name is dropped from the next create on: a phone spends seconds trying a saved network at an address that no longer exists before it takes the new one.
```
- **The fresh-per-create reason now has two forms.** The off switch's is unchanged; the give-up's is:
```
WifiDirectManager: group identity: a new network on every create (DIRECT-XX-Navegadortz2), because this unit gives the group a new address every time and a kept name only makes the phone try a saved network it cannot find first. "New WiFi Direct network identity" in Settings measures again.
```
- **The no-band fallback names the group**, one line before its `createGroup`:
```
WifiDirectManager: standard createGroup as DIRECT-WC-Navegadortz2, band left to the platform.
```
and if the unit refuses that (not expected here):
```
WifiDirectManager: this unit refused a named group with no band request (...), so the platform's own profile is asked for instead.
```
- **The read-back on a degraded unit** says `asked=temporary` and `stable=unproven (a new network is made on every create)`, not `stable=no`: the verdict reads what was asked for, so a fresh-per-create group is no longer compared to the previous one.
- INFO is enough. `log-level=2`.

---

## 3. Preparation

Settings as round 1's "every WiFi Direct run" table, `wifi-5ghz-channel=36`, `static-bssid=0`, `native-wifi-version-exchange=false`, `stand-down-station-for-wifi-direct` absent. Phone forgotten as round 1's step 0 (R2 only needs it).

### Build gate

Candidate md5 recorded and different from round 1's `b891061428dac2a8aa16b8cc3b80e958`. Identity by symbol, since the class names are the same as round 1's:

```bash
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'onStandardCreateSucceeded'          # > 0
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'resetWifiDirectIdentityMeasurement'  # > 0
unzip -p <candidate.apk> 'classes*.dex' | strings | grep -cF 'wifi-direct-group-address-moves'     # > 0
```

Unit tests: candidate **1170 / 0**, `P2pGroupIdentityPolicyTest` **16** (one more than round 1), the other three counts unchanged (5, 10, 10).

---

## 4. Runs

### R0: build gate and unit tests

As §3. A failure stops the round.

### R1: the give-up (candidate, head unit only)

Phone Bluetooth off. All four identity keys deleted and `wifi-direct-group-address-moves` deleted at the start.

1. Bring-up 1: expect `no kept network yet, so DIRECT-XX-... is drawn now`, `stable=unproven`. Seed the four keys by hand as round 1 did (name, passphrase from the supplicant config, last-group SSID and BSSID).
2. Bring-up 2: expect `asking for the kept network`, `stable=no`, **and the warning line above**. This is the app deciding; on this rig its write fails, so now write `wifi-direct-group-address-moves=true` by hand.
3. Bring-ups 3 and 4: expect the give-up reason (`because this unit gives the group a new address every time`), two **different** `DIRECT-xx-` names, `asked=temporary`, `persistent=no (temporary)`, `stable=unproven (a new network is made on every create)`. The four stored keys must be untouched: read them back.
4. Delete `wifi-direct-group-address-moves`, bring-up 5: expect `asking for the kept network DIRECT-XX` again (the measurement restarts from the kept pair).

PASS when steps 2 and 3 hold and `createGroup SUCCESS` is 1 per bring-up.

### R2: reconnect time in the degraded state, A/B (phone needed)

Candidate with the four keys seeded **and** `wifi-direct-group-address-moves=true`; baseline with none. Three phone-driven reconnects each, the round-1 way, `[TX] Wrote TYPE 3` to `WirelessServer: Incoming connection detected`.

PASS when the candidate forms all three and its three durations are within a second or so of the baseline's (round 1 measured the baseline at about 2 s). The ten-second reconnects of round 1 should be gone, since every group now has a name the phone has never seen. Report the six numbers.

### R3: the watchdog's fallback keeps the identity (candidate, head unit only)

Four keys seeded, `wifi-direct-group-address-moves` deleted, phone Bluetooth off. Launch and leave it **150 s**: the join watchdog fires at 60 s (recreate 1, still with the band request) and at about 120 s (recreate 2, the no-band fallback). Then exit.

PASS when every `group identity ssid=` line in the capture names the kept `DIRECT-WC-...` (or whatever was seeded), the third group is preceded by `standard createGroup as DIRECT-WC-..., band left to the platform.` and its read-back says `asked=persistent`, and **no** `DIRECT-TQ` (or any other older profile) appears. `grep -ac "standard createGroup as"` is at least 1 and `grep -ac "Standard createGroup SUCCESS"` matches it. If the refused-named-group line appears instead, that is a FAIL to keep the capture for: it means this unit will not take a named group without a band, which round 1 gave no sign of.

### R4: the suffix on a first launch (candidate, head unit only)

Uninstall is banned (§5), so simulate the first-launch state instead: all identity keys deleted, `force-stop`, then launch and read the first `group identity:` line's name. PASS when the suffix is `Navegadortz2` (the P2P name or the device name), not `HeadUnit`. If this ROM's `Settings.Global` `device_name` is something else, that name is also a PASS; say which it was.

---

## 5. Stop conditions and restore

- R0 fails: stop.
- R1 step 2 never prints the warning: R2 becomes INCONCLUSIVE (the give-up never triggered), run R3 and R4 anyway.

Leave the candidate installed, all five identity keys deleted, `settings.xml` restored to the round's baseline, phones' radios on, and the phone's head-unit record clean (R2 sends no endpoint, so nothing to forget).
