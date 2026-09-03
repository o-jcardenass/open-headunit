# Headunit Reloaded, wireless mode: how it completes a phone-to-phone WiFi Direct session

Teardown of `gb.xxy.hr` v8.2.0 (vc820), the APK committed on the transfer branch at `5f630384`
(`evidence/headunit-reloaded-decompile/headunit-reloaded-v8.2.0-vc820.apk`, sha256
`7ddcb31d…9e35d2`, verified byte-identical here). Decompiled with jadx 1.5.6, 4,191 classes, 79
finished with errors. Full tree kept at `~/projects/ohu-project/hur-reloaded-teardown/src_out/`;
the decisive files are copied into `evidence/` beside this document.

The app is R8-obfuscated except for its own top-level package. The wireless subsystem is the `z7`
package, 13 files, and its accept-loop bring-up was moved by R8 into
`android/support/v4/media/session/t.java`. Log tags are intact and carry the whole story:
`HUR-WirelessBT`, `HUR-Hotspot`, `HUR-DummyA2dp`, `HUR-A2dpNudge`.

## The short version

Their handshake is the *old, simple* WPP flow, announced at protocol **1.0**, with no TCP endpoint
that the phone will honour and no car-identity that the phone will parse. It works anyway. So the
reason two phones link with Reloaded and not with us is not the protocol. It is four things
around it, in this order of confidence:

1. They can read a **real BSSID on an unrooted phone**. On the WiFi Direct path that is a
   derivation from the P2P interface's IPv6 link-local address; on their LocalOnlyHotspot fallback
   it is simply `SoftApConfiguration.getBssid()` off their own reservation. We have neither route,
   and a missing BSSID is where our WiFi Direct path stops dead. Which of the two carried the
   tester's session is not knowable from the UI; see §8.
2. When they cannot read one, they **send the credentials anyway**. We abort before writing a byte.
3. Their AA RFCOMM listener is **insecure** (`listenUsingInsecureRfcommWithServiceRecord`); ours is
   the secure variant on every socket.
4. They bring the **network up first and Bluetooth second**, publish an A2DP-sink decoy service
   record next to the AA and HFP ones, and make the adapter discoverable while waiting.

## 1. The BSSID, which is the whole difference

Our WiFi Direct path aborts before writing a byte when it cannot read the group interface's MAC
(`NativeAaHandshakeManager.kt:1218-1239`, via `NativeCredentialsPolicy.onUnusableBssid` → `ABORT`).
That is the correct call given what `gearhead-requires-real-bssid` measured: Gearhead joins with a
`WifiNetworkSpecifier` whose BSSID match pattern checks every bit, and credentials with no BSSID
draw `WIFI_INVALID_BSSID` and type-6 `status=-3` forever. Our six-deep chain
(`WifiDirectManager.kt:524-610`) is `NetworkInterface.getHardwareAddress` → last known → local
device address → `group.owner.deviceAddress` → sysfs / `ip link` → `Settings.Secure` → reflection
over `WifiP2pGroup`'s fields. On an unrooted phone every one of those is blocked or masked, which
our own code says out loud at `:1222-1228`.

Reloaded runs **eleven** routes (`z7/f.java:155-252`), and two of them are ones we do not have.

**The one that matters: the IPv6 link-local EUI-64 derivation** (`z7/j.java:187-208`, tried second,
ahead of every sysfs and shell route):

```java
if ((inetAddress instanceof Inet6Address) && inetAddress.isLinkLocalAddress()) {
    byte[] address = inetAddress.getAddress();
    if (address.length == 16 && (address[11] & 255) == 255 && (address[12] & 255) == 254 &&
        (strN = n(String.format("%02X:%02X:%02X:%02X:%02X:%02X",
            Integer.valueOf((address[8] & 255) ^ 2), Integer.valueOf(address[9] & 255),
            Integer.valueOf(address[10] & 255), Integer.valueOf(address[13] & 255),
            Integer.valueOf(address[14] & 255), Integer.valueOf(address[15] & 255)))) != null) {
        return strN;
```

It reads an *address*, not a hardware identity, so none of the MAC restrictions apply:
`NetworkInterface.getInetAddresses()` is not gated the way `getHardwareAddress()` has been since
API 23. When the kernel generated the interface's link-local address by the EUI-64 rule, that
address contains the MAC: bytes 11 and 12 are the `ff:fe` marker, bytes 8-10 and 13-15 are the six
MAC bytes, and bit 1 of byte 8 is the flipped U/L bit, which the `^ 2` undoes. The `ff:fe` check is
self-validating: an interface using RFC 7217 stable-privacy addressing fails it and the
chain moves on, so the route cannot return a fabricated address.

The other new one is route 1, reflection on `WifiP2pGroup.getGroupOwnerBssid()` (`f.java:155`),
which is not in the public SDK and is presumably present on some vendor trees; it is cheap and
tried first.

**And when all eleven fail, they send anyway.** `f.java:251` leaves the BSSID null, the record is
built with it null (`f.java:319`), and the handshake prints a warning and omits the protobuf field
rather than aborting (`z7/p.java:420-422`, `:455-457`):

```java
if (str8 == null) {
    Log.e("HUR-WirelessBT", "No BSSID available - the phone will likely FAIL to join the AP. Ensure Location (GPS) is enabled on this head-unit device.");
}
...
String str12 = (String) bVar.f7187d;
if (str12 != null) { gVarF.f7790d |= 4; gVarF.f7793g = str12; ... }
```

Note the difference from ours in *both* directions: they omit the field where we send an empty
string (`WppMessages.infoResponse` sets `bssid.orEmpty()`), and they proceed where we abort.

One inconsistency in their own code worth knowing: on the LocalOnlyHotspot path a missing BSSID
*is* fatal (`z7/h.java:226-232`, `L("Could not obtain AP BSSID (enable Location/GPS)")`). Only the
P2P path is permissive.

## 2. The Bluetooth surface

| | Reloaded | Ours |
|---|---|---|
| AA channel | `listenUsingInsecureRfcommWithServiceRecord("AndroidAuto", 4de17a00-…)` (`t.java:413`) | `listenUsingRfcommWithServiceRecord("AA BT Listener", …)` (`NativeAaHandshakeManager.kt:330`) |
| HFP record | `listenUsingRfcommWithServiceRecord("HFP", 0000111e-…)`, **only if the adapter does not already advertise HFP** (`t.java:414-421`, gate at `p.java:85-113`) | always, `"Hands-Free Unit"` (`:357`) |
| A2DP sink decoy | `listenUsingInsecureRfcommWithServiceRecord("HUR Audio Speaker", 0000110B-…)` (`t.java:428`) | none |
| Discoverable | `setScanMode(23)` by reflection once listening, `setScanMode(21)` the instant a phone connects (`p.java:525-550`, `t.java:454`, `:495`, `:599`) | none |
| Poke | RFCOMM connect to `00001112` (HSP-AG), hold 30 s polling every 500 ms, one pass over the selected devices, no rearm (`c/k.java`, `g4/m.java`) | `BluetoothWakePolicy.POKE_TARGETS` = `111f` then `1112`, 15 s hold on a 15 s gap, retried indefinitely under `NativeHandoffPolicy` |
| Bonding | none, no `createBond` anywhere | refuses to poke an unpaired device (`:621-627`) |

**The insecure listen is worth a look, with a caveat.** `listenUsingInsecureRfcommWithServiceRecord`
accepts a connection with no authentication and no encryption; the secure variant requires an
authenticated link. Where the two devices are already bonded, which they are on any setup that got
as far as Android Auto offering wireless, this changes nothing, so it is not an explanation on its
own. It matters only if the bond is missing or its link key has been dropped on one side, which is
a state a phone acting as a head unit reaches more easily than a car does. Reloaded uses the
insecure form for the AA channel and the A2DP decoy, and the secure form only for the HFP record it
may not even register.

**Their HFP responder is materially richer than ours.** Ours answers `AT+BRSF`, `AT+CIND=?`,
`AT+CIND?` and `OK` to everything else (`NativeAaHandshakeManager.kt:501-545`). Theirs
(`p.java:115-160`) adds `AT+CHLD=?` → `+CHLD: (0,1,2,3)`, `AT+BIND=?` → `+BIND: (1,2)`,
`AT+BIND?` → two indicator lines, and `AT+CGMI`/`AT+CGMM`/`AT+CGMR` returning `"HUR"`,
`"Headunit Reloaded"`, `"1.0"`; its `+CIND` carries all seven standard indicators rather than the
first two. It also drives the SLC from the *other* side when it is the initiator (`c/n.java`:
`AT+BRSF=0` → `AT+CIND=?` → `AT+CIND?` → `AT+CMER=3,0,0,1`) and then polls `AT+CIND?` every 2000 ms
as a keepalive (`g4/m.java:139-142`).

It emulates no real profile: there is no `getProfileProxy`, no `BluetoothA2dp`, no
`BluetoothHeadset` anywhere in the APK. The A2DP "sink" accepts an RFCOMM socket and discards bytes
(`z7/d.java:28-39`). Real A2DP runs over L2CAP PSM 0x19, so nothing will ever stream into it. It
exists solely to put `AudioSink` in the SDP response.

## 3. Ordering: the network comes up first

`TransporterService.onCreate` constructs the network object and hands it a callback pair
(`TransporterService.java:236-238`). Bluetooth is not touched until the callback fires with a
complete record: SSID, passphrase, BSSID, IP and frequency (`l5/b.java:24-30`). Only then does
`t.M()` open the three listeners, and the line it logs says so: `"Wireless AA RFCOMM listener
started (AP already up)"` (`t.java:600`). If the network cannot be created, there is no Bluetooth at
all: `"Could not create WiFi AP, wireless AA unsupported: …"` (`t.java:382-386`).

That is ZLink's ordering too (memory `zlink-softap-native-aa-reference`: AP → IP → port → then
Bluetooth). Ours is the reverse: `start()` opens the RFCOMM listeners immediately and the handshake
then waits up to 60 s for credentials to arrive (`CREDENTIALS_WAIT_MS`), asking for a refresh every
10 s. Our order is deliberate, since it lets the TCP server come up on a unit whose adapter the
phone cannot reach, but it means we can accept a phone before we have anything to tell it.

## 4. The network they hand over

**P2P is tried first, LocalOnlyHotspot is the fallback.** `z7/j.java:293-338` initialises
`WifiP2pManager` (with a **null** `ChannelListener`), fires `requestDeviceInfo` purely to cache a
MAC for the chain above, always `removeGroup` first, then:

```java
if (Build.VERSION.SDK_INT >= 29) {
    try {
        jVar.f13292c.createGroup(jVar.f13293d, new WifiP2pConfig.Builder().setGroupOperatingBand(2).build(), iVar);
        Log.d("HUR-Hotspot", "P2P createGroup requested on 5 GHz band");
        return;
    } catch (Throwable th) {
        Log.w("HUR-Hotspot", "5 GHz P2P config unavailable, using default band: " + th);
    }
}
jVar.f13292c.createGroup(jVar.f13293d, iVar);
```

Points of comparison with `WifiDirectManager`:

- **No `setNetworkName`, no `setPassphrase` anywhere in the APK.** They take whatever the framework
  mints and read it back. We generate `DIRECT-xx-<name>` and a 12-character passphrase on every
  create (`WifiDirectManager.kt:1318-1330`).
- **No retry ladder.** `createGroup` failing logs the raw reason and goes straight to
  `startLocalOnlyHotspot` (`z7/i.java:36-55`). No band-mismatch retry, no channel ladder, no
  `setGroupOperatingFrequency` (which does not appear in the APK at all), no persistent-group
  handling. Where #907's unit answers `BUSY` 27 times, Reloaded would fall through to a hotspot on
  the first refusal.
- **No watchdog and no group lifecycle.** Once the credentials are published, the network object
  does nothing further; there is no join watchdog, no re-creation on peer loss, no `ChannelListener`.
- The band is read back reflectively (`getFrequency()`) but is **not corrective**: it only decides
  whether the projection is capped to 720p (`t.java:397-405`).

**`startLocalOnlyHotspot` is the route we have no equivalent of.** `j.java:333` / `i.java:49` call
it with no configuration, then read SSID, passphrase and, on API 30+, the **BSSID** straight off
the reservation (`h.java:56-62`):

```java
MacAddress bssid = softApConfiguration.getBssid();
Log.d("HUR-Hotspot", "LocalOnlyHotspot SoftApConfiguration.getBssid() = " + bssid);
```

That is an app-owned AP whose own configuration is readable without any privileged permission,
which is exactly what `test-rig-reads-but-cannot-write-softap-config` says the system hotspot is
not. Its costs are real: no band control (their record hardcodes frequency `0`, so their own code
always assumes not-5 GHz and caps to 720p), no internet on the host, and it dies with the app.

## 5. Retries and timings

| What | Reloaded | Ours |
|---|---|---|
| First `requestGroupInfo` after create | 1200 ms (`i.java:68`) | on `WIFI_P2P_CONNECTION_CHANGED` etc. |
| Group-info null retry | 1000 ms × 10, then give up (`f.java:64-71`) | 15 × 1 s for the IP, plus the join watchdog |
| Sleep before writing credentials | **1000 ms** (`p.java:424`) | none |
| Poke | 30 s hold, 500 ms poll, **one pass, no rearm** (`c/k.java:167`) | 15 s hold / 15 s gap, indefinite |
| AA listener after a session starts | left open; the accept loop simply loops (`t.java:442-470`) | closed on `CompleteSuccess` (`:1046-1056`) |
| Keepalive | HFP `AT+CIND?` every 2000 ms while the SLC is up | WPP type 8 echo only |

## 6. The protocol, recovered exactly

They use the full protobuf runtime, so the APK carries the serialized `FileDescriptorProto` for
their WPP schema. It is extracted and decoded here: `evidence/bluetooth-recovered.proto`, from
`m7/a.java:78`. The file is `bluetooth.proto`, package `androidauto.bluetooth.proto`,
`java_package = gb.xxy.hr.btproto`. That is Google's own naming, so this is a generation of the
schema rather than a reconstruction.

What it says that we did not have from a schema before:

- `WifiVersionRequest`: `major_version = 1`, `minor_version = 2`, **`repeated int32
  supported_wifi_channels = 3`**, **`car_info = 4` (`HeadUnitInfo`)**, **`wifi_projection_protocol_info
  = 5`**.
- `HeadUnitInfo`: `make 1, model 2, year 3, vehicle_id 4, head_unit_make 5, head_unit_model 6,
  head_unit_software_build 7, head_unit_software_version 8`. **Eight fields, no `body_type`.**
- `WifiVersionResponse` carries a sixth field we do not model: `WifiDeviceInfo { device_id 1,
  connectivity_lifetime_id 2 }`. `WifiChannelType` is `CHANNELS_5GHZ_ONLY = 0`,
  `CHANNELS_24GHZ_ONLY = 2`, `CHANNELS_DUAL_BAND = 3`.
- `WifiConnectStatus` has `error_message_hint = 2` (a string) beside the status.
- `WifiStartResponse` is `ip_address 1, port 2, status 3`; our schema leaves field 2 unclaimed and
  it is `port`.
- The `WifiStatus` enum in full, with its negative values: `STATUS_SUCCESS 0`,
  `STATUS_UNSOLICITED_MESSAGE 1`, `NO_COMPATIBLE_VERSION -1`, `WIFI_INACCESSIBLE_CHANNEL -2`,
  `WIFI_INCORRECT_CREDENTIALS -3`, `PROJECTION_ALREADY_STARTED -4`, `WIFI_DISABLED -5`,
  `WIFI_NOT_YET_STARTED -6`, `INVALID_HOST -7`, `NO_SUPPORTED_WIFI_CHANNELS -8`,
  `INSTRUCT_USER_TO_CHECK_THE_PHONE -9`, `PHONE_WIFI_DISABLED -10`.
- `SecurityMode` matches ours exactly, including `WPA2_PERSONAL = 8`.

**This is an older generation than the 17.5 layout.** `wireless-startup-and-wpp-reference.md` §4,
decoded from Gearhead 17.5's own dex, has an enum at field 3, the repeated int32 at field 4,
`car_info` at 5 and `wpp_info` at 6; and our own hardware evidence agrees with it, because a phone
did store the endpoint we sent on field 6 (`wpp-tcp-endpoint-poisons-the-phone`). Google inserted a
field and everything after it shifted, so on a 17.x phone Reloaded's type 4 lands like this:

- field 3, their six frequencies, arrives on an enum. Wire type matches, the values are not valid
  enum constants, so they go to unknown fields.
- field 4, their `HeadUnitInfo`, arrives on `repeated int32`. Length-delimited *is* the packed
  encoding, so the phone really does decode those bytes as a list of integers, and it does not
  throw, because the message's last byte is the `0` of `"1.0"` with its continuation bit clear.
  Garbage channel numbers, silently kept.
- field 5, their `ip:5288` endpoint, arrives on `car_info`. `ip_address` is a string on field 1 and
  so is `make`, so the phone reads the car's make as `"192.168.49.1"`; their port is a varint where
  `model` expects a string, which mismatches and goes to unknown fields.

Nothing throws, the phone answers type 5, and the exchange proceeds. That is worth stating plainly
because it is the strongest evidence in this teardown for what the flow does *not* need.

Two conclusions follow, and both are load-bearing for us:

- **A working wireless session needs none of the car identity.** Reloaded's never arrives in a form
  the phone can read, and the session starts regardless. Our `WppCarInfo` work is for the TCP
  endpoint's allowlist gate, not for the ordinary RFCOMM flow.
- **They announce version `1.0`** (`p.java:232-236`, `major=1, minor=0`), which is below the 4.1
  gate, so the phone discards the endpoint they advertise on principle. That is why they never hit
  what `wpp-tcp-endpoint-poisons-the-phone` measured: their endpoint is inert, so nothing is stored
  and no reconnect is bricked. Our version-exchange setting defaults off for the same net effect.

Their exchange, in order (`p.java:193-523`):

```
on connect        -> type 4  WifiVersionRequest  (version 1.0, six channels, car info, ip:5288)
type 5 received   -> type 1  WifiStartRequest    (ip, 5288)
type 2 received   -> sleep 1000 ms, type 3 WifiInfoResponse
type 8 received   -> type 9  echoing the timestamp
type 6 / type 7   -> logged only, no branch
```

`security_mode` is hardcoded to `8` (`WPA2_PERSONAL`) and `access_point_type` to `0` (`STATIC`) on
both transports (`p.java:434-443`), including the LocalOnlyHotspot one, where the reference
implementations and we both use `DYNAMIC`. Their framing is byte-identical to ours: two-byte
big-endian length, a zero byte, then the type (`p.java:176-191`).

## 7. What to change on our side, ranked

Everything here lands on `connection/wifi/modes/nativeaa/` and `connection/wifi/direct/`. The
current branch `fix/wpp-over-tcp` (six commits) touches the same files but none of the same
decisions, so all of it is independent of that branch and could go on `main` instead.

### 1. Add the IPv6 link-local route to the BSSID chain: highest value, lowest risk

`WifiDirectManager.kt:524-610`. Insert it as rung 1, before `getWifiDirectMac(iface)`: enumerate
the group interface's `Inet6Address` link-local addresses, require the `ff:fe` marker at bytes 11
and 12, and rebuild the MAC with the U/L bit flipped. Fall back to any interface passing the
existing p2p/wlan/ap name filter. The marker check makes it self-validating, and
`SoftApBssidPolicy.isUsable` already rejects the placeholders, so a wrong answer cannot reach the
wire.

This is the only candidate that plausibly explains the whole symptom: it is the one BSSID source an
unrooted phone can read, it is the reason their permissive fallback is rarely exercised, and our
own abort at `NativeAaHandshakeManager.kt:1218-1239` is exactly where a phone-as-head-unit stops.

There is a cheaper check before any code is written, and it settles whether the route exists at all
on the rig's hardware. With a group up, from a shell on the head-unit phone:

```
ip -6 addr show dev p2p-wlan0-0     # or whichever p2p-* interface exists
ip link show dev p2p-wlan0-0        # root/privileged; for the comparison only
```

If the link-local is `fe80::xxxx:xxff:fexx:xxxx`, with the `ff:fe` in the middle, the address is
EUI-64 derived and the route works. If it looks random, the interface uses RFC 7217 stable-privacy
addressing and this route returns nothing on that device, which is exactly what the marker check in
their code guards against.

After that it costs one round on the two-phone rig: mode 3 / WiFi Direct with the new rung
instrumented, reading whether the chain answers before the abort at
`NativeAaHandshakeManager.kt:1219`. If it does, the static-BSSID setting stops being the only fix
for a phone acting as the head unit.

### 2. Reconsider the hard abort on a missing BSSID

`NativeCredentialsPolicy.onUnusableBssid` returns `ABORT` for `WIFI_DIRECT`. That was measured
right in August: no-BSSID credentials drew `WIFI_INVALID_BSSID` and `status=-3` on every retry. It
is still right *as an expectation*, but Reloaded's behaviour argues for making it a warning rather
than a stop: sending produces a refusal the log can name, aborting produces nothing at all, and
`ConnectionIssues.raise(BSSID_UNAVAILABLE)` already carries the user-facing verdict either way. One
detail to copy exactly if we do: they **omit** the field, we send `""`. Our own comment in
`wireless.proto` argues for the empty string on the grounds that a strict parser may reject a
missing `required` field, and their schema does declare it `optional`, so the two positions are
each defensible, but only one of them has been observed to complete a session, and it is not ours.

Do this only after item 1, and only if item 1 does not fix the case: with a real BSSID in hand the
question does not arise.

### 3. Consider LocalOnlyHotspot as a native-AA transport for a phone head unit

`NativeStrategy` today is `WIFI_DIRECT` or `HOTSPOT`, and the hotspot arm reads a soft AP we did not
create, which is why `hotspotSsid`, `hotspotPassword`, `hotspotInterface` and the static BSSID all
have to be typed in by hand, and why `test-rig-reads-but-cannot-write-softap-config` exists.
`startLocalOnlyHotspot` is a third thing: an AP we own, whose SSID, passphrase and (API 30+) BSSID
all read back off the reservation with no privileged permission and nothing for the user to type.

Its costs are real and would have to be stated in the setting: no band control at all (their
own record hardcodes the frequency to `0`, and their video caps to 720p on every LOH session as a
result), no internet on the host while it runs, and the AP dies with the app. On a dedicated head
unit it is strictly worse than what we have. On a phone standing in for one it may be the only
route that needs no manual configuration, and `hotspot-route-needs-5ghz` is the reason to be
cautious rather than enthusiastic.

Worth scoping only after item 1 is measured. If the IPv6 rung works, WiFi Direct keeps 5 GHz and
this is unnecessary.

### 4. Make the AA RFCOMM listener insecure

`NativeAaHandshakeManager.kt:330` and `:424`. `listenUsingInsecureRfcommWithServiceRecord` for the
AA UUID, matching Reloaded and matching what an insecure record means on the wire: the phone
connects without the link being authenticated first. Keep the HFP record secure, as they do.

This is a one-line change with a real failure mode of its own (anything can connect to the record),
and it is untested by anyone here, so it wants its own arm in a rig round rather than being folded
into item 1.

### 5. The service record name and the decoy records

Their AA record is named `"AndroidAuto"`, ours `"AA BT Listener"`. Nothing in the protocol reads it,
but it is what a phone shows and what a phone's own heuristics see, and the cost of matching them
is nil. The A2DP-sink decoy (`0000110B`, accepting and discarding) and the discoverability toggle
are cheap for the same reason: a head unit that advertises `AudioSink` and is discoverable looks
more like a car than one that advertises neither. None of this is proven to matter; it is the
cheapest possible arm to add to a round that is running anyway.

### 6. Bring the network up before the Bluetooth listeners

Ours opens RFCOMM first and waits 60 s for credentials; theirs and ZLink's both refuse to advertise
until the network is up and its address is known. Our order exists so `WppTcpServer` can start on a
unit whose adapter is unreachable (`NativeAaHandshakeManager.kt:282-287`), which is a good reason.
So this is not "swap the order" but "do not accept an AA connection we cannot serve". The narrow
version: keep the listeners open, and hold the accept until the credential snapshot exists, rather
than accepting and then waiting inside the handshake.

### 7. Schema corrections, from the recovered descriptor

`app/src/main/proto/wireless.proto` and its committed `Wireless.java` (regenerate with protoc 25.1,
per CLAUDE.md):

- `WifiStartResponse.port = 2`, currently unclaimed in ours.
- `WifiConnectStatus.error_message_hint = 2` (string), a free diagnostic on the one message that
  reports whether the phone got onto the network.
- `WifiVersionResponse.device_info = 6` (`device_id`, `connectivity_lifetime_id`).
- `WppCarInfo.body_type = 9` does not exist in their schema. Ours describes it as matched by
  position against `ServiceDiscoveryResponse` rather than read from a capture; the recovered
  descriptor is evidence against it, though it is evidence from an older generation.
- Their `supported_wifi_channels` is a `repeated int32` of **MHz** values, matching what §4 of the
  reference says the 17.5 field at position 4 is. We send neither.

Leave the `car_info = 5` / `wpp_info = 6` placement alone: the 17.5 dex decode is the more current
authority and the field numbers demonstrably shifted between generations.

### Not worth taking

- Their P2P bring-up. No band retry, no channel ladder, no watchdog, no `setGroupOperatingFrequency`,
  a null `ChannelListener`, and `removeGroup` failures ignored outright. Every one of those is a
  problem we have already been bitten by and fixed.
- Their poke. One pass over the device list with no rearm, and the tag says A2DP while the UUID is
  HSP-AG.
- Their teardown. `removeGroup` with a null listener, no `Channel.close()`, and a dead code path
  (`h.java:225-242`) that can never run because the flag it tests is always set by the only two
  callers that reach it.

## 8. The app around it: manifest, modes, ports

### There is no "WiFi Direct transport" to select

The brief describes the tester picking connection type **Wireless** and transport method **WiFi
Direct**. In this build those are not two choices. The `wifi_mode` `ListPreference` that carries
`"Use WiFi Direct (also know as WiFi P2P)"` still exists in `res/xml/connection.xml` and
`res/values/arrays.xml:72-85`, but that screen is never inflated: its only consumer is the one-shot
default seeding in `DispatcherActivity.onCreate:30-36`, the settings UI is Compose and writes no
control for it, and the single read in the whole app is a teardown branch
(`TransporterService.java:375-386`). The live home screen offers four buttons: **Wireless**, **Dev
Wireless**, **USB**, and phone/self mode (`j8/m.java:130-140`).

So "Wireless" is the button, and WiFi Direct is simply what it tries first, with LocalOnlyHotspot
behind it automatically. That matters for reading the tester's report: **we do not know from the UI
alone which of the two carried their session.** The log line separates them:
`"WiFi-P2P failed - falling back to LocalOnlyHotspot (video will cap to 720p)"` (`z7/i.java:39`)
versus `"P2P createGroup requested on 5 GHz band"` (`z7/j.java:64`), and it is worth asking for,
because if their two-phone link actually ran on LocalOnlyHotspot then the reachable BSSID came off
`SoftApConfiguration.getBssid()` and not from the IPv6 route, and the fix for us changes shape.

### Their TLS role agrees with what we decoded from Gearhead

`c8/c.java:197-222` builds an `SSLEngine` (not an `SSLSocket`) on TLSv1.2 from a PKCS12 keystore in
`res/raw` with the password `"aa"`, an accept-everything trust manager, and `setNeedClientAuth(true)`.
Then `p/j3.java:57` calls **`setUseClientMode(true)`** before `beginHandshake()`. So the head unit
is the TCP *server* on 5288 and the TLS *client* on the same socket, which is the same role
assignment `wpp-tcp-head-unit-is-the-tls-client` decoded from Gearhead's own bytecode. Their
`setNeedClientAuth(true)` is a no-op in client mode. Independent corroboration of a thing that cost
us a hardware round.

### Ports

`5288` is the only bind in the app (`p7/b.java:53-55`), and it is bound in `onCreate`, long before
any handshake. The handoff from Bluetooth to projection is entirely implicit, with the accept
already blocking when the phone dials. `5277` is outbound only, used by phone/self mode and by Dev
Wireless. `GalConstants.WIFI_PORT_VALUE = 30515` is declared and never used.

Their phone/self mode splits on the AA version exactly where our own reference says it should
(`TransporterService.java:167-200`): at 17.4 and above it dials `127.0.0.1:5277`, below it
broadcasts to `WirelessStartupReceiver` with `ip_address` and `projection_port` extras.

### Manifest

Nothing privileged and nothing we lack. `LOCAL_MAC_ADDRESS` is declared (line 52) but it is
`signature|privileged` and never granted to a normal app, which is the whole reason the eleven-route
BSSID chain exists. `NEARBY_WIFI_DEVICES` is declared with `minSdkVersion="32"` where the platform
gate is 33. `BLUETOOTH_SCAN` carries no `neverForLocation`. Two of their declarations are inert:
`android.permission.ACTION_MANAGE_WRITE_SETTINGS` is an intent action, and
`android.hardware.usb.host` is a feature name. `TransporterService` is `exported="false"` with
`foregroundServiceType="connectedDevice"`, and its `BOOT_COMPLETED` intent filter can never fire,
which makes their `RECEIVE_BOOT_COMPLETED` dead too.

`libunrooted_android.so` ships for all four ABIs and **is never loaded**: only `usb1.0`, `hur` and
`androidx.graphics.path` are, and the ELF dependency runs the other way. It is a leftover from the
libusb-android build, not a privileged-helper route. That lead is closed.

The one root-dependent feature is unrelated to any of this: `c8/b.java:141-155` shells out to `su`
to `appops set <pkg> SYSTEM_ALERT_WINDOW` for user-listed dialer packages.

### Two lifecycle details worth stealing the idea of

- **The dummy HFP record is dropped once video focus is granted** (`y7/i.java:151-162`,
  `"VideoFocus acquired - dropping dummy HFP (bootstrap complete)"`). The decoy exists only to get
  the session started and is retired the moment the session is demonstrably live.
- **A hard four-hour self-stop** (`TransporterService.java:256`, `k7/b.java:88-97`).
