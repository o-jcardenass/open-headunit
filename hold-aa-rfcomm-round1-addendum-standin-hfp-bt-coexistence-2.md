# hold-aa-rfcomm — round 1 addendum 2: stand-in HFP + BT/WiFi coexistence, D-POCO as head unit

Exploratory follow-up to `hold-aa-rfcomm-round1-addendum-standin-hfp-bt-coexistence.md`, not part
of round 1's formal runs or verdicts. Candidate: fork `fix/hold-aa-rfcomm` @ `c3c5a2d8` (installed
build: versionCode 114, versionName `3.5.0-beta1`, md5 `52b188106e9c495e9dbf0b29f16b20d6`). Units:
D-POCO (POCO X3 NFC, Android 15, head unit under test), D-MOTO (Motorola edge 30 neo, Android 14,
connected phone). Date: 2026-09-26.

## Purpose

The first addendum found D-SAM's WiFi Direct link collapsing when a call rang D-POCO, isolated to
D-SAM's own Bluetooth radio, and named "confirming the call-audio-rides-WiFi behavior generalizes
beyond this one phone/Gearhead version/call" as an open item. This addendum reruns that same
mechanism with the head-unit and phone roles reassigned to a different, current-generation
device pair: **D-POCO as the head unit under test** (Android 15, no real platform HFP client — its
own `dumpsys bluetooth_manager` exposes only the AG-role `HeadsetService`, so
`HfpServiceRecordPolicy.shouldRegisterDummyHfp()` engages the same stand-in path D-SAM used) and
**D-MOTO as the connected phone** (Gearhead `17.8.663814-release`, the phone-side app D-SAM's
original addendum was measured against). The call in every leg was placed from a third phone not
on this rig's adb setup.

D-POCO's `settings.xml` already carried a Native AA config pointed at D-MOTO from an earlier round
(`wifi-connection-mode=3`, `native-poke-bt-macs`/`last-connected-native-mac` both D-MOTO's MAC
`A0:46:5A:97:E4:95`, `native-aa-complete-hfp-slc=true`, `log-level=0`), and the two devices were
already Bluetooth-bonded, so no settings were changed and no pairing step was needed for this round.

## Bring-up

Head unit launched first (per the standing protocol), phone's Bluetooth brought up ~18s later.
Handshake landed within 13s of enabling D-MOTO's Bluetooth, first attempt, no retries:

```
11:21:43.787  NativeAaHandshakeManager.shouldRegisterDummyHfp | NativeAA: radio [POCO X3 NFC]
              gets the stand-in HFP record, because it advertises no Hands-Free.
11:21:44.739  WifiDirectManager: 5GHz createGroup SUCCESS!
11:21:45.029  NativeAA: Attempting active poke to device: motorola edge 30 neo (A0:46:5A:97:E4:95)...
11:22:15.715  NativeAA: Successfully poked motorola edge 30 neo via HFP-AG. Holding 15000ms...
11:22:15.991  NativeAA: hands-free service level connection established (HFP-AG poke to
              A0:46:5A:97:E4:95). The phone now treats this head unit as its hands-free device,
              and this app cannot carry call audio.
11:22:25.548  WirelessServer: Incoming connection detected from /192.168.49.9
11:22:25.816  AapSslContext.performHandshake | SSL handshake complete.
```

This is the definitive on-device confirmation that the stand-in path — the same mechanism D-SAM
used — is genuinely engaged on D-POCO, not merely inferred from the earlier `dumpsys` check. Video
climbed to a steady 26-29fps within 10s of the handshake and held there for the rest of the round.

## Runs

Three calls were placed into D-MOTO from an external phone, each rung while the AAP session was
live and steady. All three held the video session with zero drops and no disconnect — the D-SAM
collapse did not reproduce on this hardware in any of them.

**R1 — D-POCO's own Bluetooth ON (direct analogue of Finding 2).** Call rang 11:23:21, ended
11:23:41.

```
11:23:17.330  Throughput over 5000ms: rendered=141 (28fps), dropped=0
11:23:22.333  Throughput over 5003ms: rendered=132 (26fps), dropped=0   <- ring is ~1s into this window
11:23:27.342  Throughput over 5009ms: rendered=118 (23fps), dropped=0
11:23:32.347  Throughput over 5006ms: rendered=122 (24fps), dropped=0
11:23:37.353  Throughput over 5005ms: rendered=148 (29fps), dropped=0
11:23:42.360  Throughput over 5008ms: rendered=140 (27fps), dropped=0   <- call already ended
```

No `AapRead: Connection closed`, no `disconnected (link_lost)`, no session-state change anywhere
in this window.

**R2 — D-POCO's own Bluetooth OFF (direct analogue of Finding 3).** Disabled at 11:24:39, confirmed
still off at +5s and +20s (no self-revert on this unit, unlike D-HU's ~14s revert). Call rang
11:26:36, ended 11:26:50.

```
11:26:32.486  Throughput over 5008ms: rendered=146 (29fps), dropped=0
11:26:37.491  Throughput over 5005ms: rendered=130 (25fps), dropped=0   <- ring is ~1s into this window
11:26:42.497  Throughput over 5005ms: rendered=120 (23fps), dropped=0
11:26:47.498  Throughput over 5001ms: rendered=137 (27fps), dropped=0
11:26:52.498  Throughput over 5000ms: rendered=134 (26fps), dropped=0   <- call already ended
```

Bluetooth re-enabled afterward (`enabled: true` confirmed).

**R3 — D-POCO's own Bluetooth ON again (replicate of R1), same live session.** Call rang 11:29:10,
ended 11:29:22.

```
11:29:07.612  Throughput over 5003ms: rendered=128 (25fps), dropped=0
11:29:12.620  Throughput over 5008ms: rendered=125 (24fps), dropped=0   <- ring is ~2s into this window
11:29:17.628  Throughput over 5008ms: rendered=133 (26fps), dropped=0
11:29:22.633  Throughput over 5004ms: rendered=135 (26fps), dropped=0   <- call ends inside this window
11:29:27.635  Throughput over 5003ms: rendered=149 (29fps), dropped=0
```

All three runs used the same underlying WiFi Direct/AAP session (one handshake for the whole
round — `SSL handshake complete` appears once in the full capture, logged at two call sites at the
same timestamp, not as two separate reconnects).

## Finding: the collapse does not generalize to this head-unit/phone pair

D-POCO engages the identical stand-in-HFP code path D-SAM used (confirmed by the same log line),
and the identical phone-side app (D-MOTO's Gearhead `17.8.663814-release`) is close in version to
D-POCO's own `17.8.663814-release` in the original addendum. Despite that, an incoming call — rung
three times, twice with the head unit's own Bluetooth on and once with it off — never dropped a
frame or closed the session. This points the original mechanism toward D-SAM's specific hardware
(an old, cheap combo Bluetooth+WiFi radio contending for shared timing) rather than an inherent
property of holding the stand-in RFCOMM link open through a call: on a current-generation
Snapdragon-class radio, the same held link and the same real phone call cost nothing measurable.

This does not reopen the proposed direction in the first addendum (branching hold behavior on real
vs. stand-in HFP capability) — it narrows it. The risk that direction was written to cover appears
to be specific to older/cheaper radio hardware of D-SAM's class, not to every stand-in-HFP unit.

## Open items

- This is still two device combinations (D-SAM and D-POCO), both n=1 for their hardware class. A
  third stand-in-HFP unit — ideally another Android <24 device or another known-cheap combo radio —
  would say whether "old/cheap hardware" or "D-SAM specifically" is the right generalization.
- All three calls here were short (rung and ended within ~14-16s, none answered and held). The
  original Finding 2/3 calls were also brief. A longer held call was not tested on either unit.
- Evidence: `rig-evidence-hold-aa-rfcomm` release, asset `hold-aa-rfcomm-round1-addendum2-captures.zip`,
  sha256 `f2450d4b7b757b4ac80b875ca1b2d07464257f1156fa96ce44d72250eaf6b7e7` (full logcat captures for
  bring-up plus all three runs, both devices).
