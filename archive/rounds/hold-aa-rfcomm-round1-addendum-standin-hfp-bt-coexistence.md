# hold-aa-rfcomm — round 1 addendum: stand-in HFP + BT/WiFi coexistence on D-SAM

Exploratory follow-up done alongside round 1, not part of its formal runs or verdicts.
Candidate: fork `fix/hold-aa-rfcomm` @ `c3c5a2d8`. Units: D-SAM (Samsung SM-T230, Android
4.4.2/API 19, 2.4GHz-only WiFi, head unit), D-POCO (POCO X3 NFC, Gearhead
`17.8.663814-release`, phone), D-HU (UNISOC MT50, Android 14, 5GHz WiFi, head unit, real
platform `BluetoothHeadsetClient`), D-MOTO (caller). Date: 2026-09-26.

## Background

D-SAM's Android 4.4.2 predates `BluetoothHeadsetClient` (added API 24), so it can't make a
real platform-level Bluetooth hands-free-client connection to a phone. To still satisfy
Android Auto's "something is holding this channel present" requirement — this branch's own
subject — the app publishes its own synthetic "stand-in" Hands-Free SDP record and answers
AT commands itself (`HfpServiceRecordPolicy`, `HfpAtResponder`, `NativeAaHandshakeManager`)
rather than going through the OS's real Bluetooth Handsfree Profile stack.

## Finding 1 — an earlier "call doesn't show on AA screen" report was Do Not Disturb, not this mechanism

First reproduction (D-SAM+D-POCO, candidate switch on): a call from D-MOTO rang normally on
D-POCO but never appeared on D-SAM's projected screen. Log evidence pinned the cause
precisely — D-POCO's own Do Not Disturb (`zen_mode=1`,
`priorityCallSenders=PRIORITY_SENDERS_STARRED`) suppressed the ring:

```
IncomingCallFilterGraph: Filter DndCallFilter done, result: [Allow, logged, notified, DND suppressed]
Ringer: shouldRingForContact: returning computation from DndCallFilter
Event: RecordEntry TC@10: SKIP_RINGING, Inaudible: isVolumeOverZero=true, shouldRingForContact=false
Ringer: ringer & haptics are off, user missed alerts for call
```

With DND off, a later run on the exact same D-SAM stand-in setup showed the call on D-SAM's
screen correctly. The stand-in mechanism does not block Android Auto's call UI — the first
result was a device setting, not an app limitation.

## Finding 2 — D-SAM's WiFi Direct link dies when the phone rings; D-HU's does not

With DND off, same call (D-MOTO → D-POCO), two setups:

**D-SAM (2.4GHz, stand-in HFP), switch off:**
```
23:47:29.985  call created on D-POCO (starts ringing)
23:47:30.294  D-SAM video throughput collapses 30fps -> 2fps
23:47:35.299  video at 0fps
23:47:36.591  AapRead: Connection closed (EOF). Disconnecting. -> session state disconnected (link_lost)
23:47:45.154  call is actually answered
```
The session was already dead for ~9 seconds before the call was answered. The trigger is
the phone ringing, not the answer action.

**D-HU (5GHz, real platform `HeadsetClientService`), same call:** video held a steady
29-30fps continuously through ringing (`23:51:46.911`), answering, and hangup
(`23:52:13.113`) — zero interruption, and the call displayed correctly on D-HU's screen.

This is not a general "Android Auto + incoming call" problem — it did not reproduce on a
real head unit with a genuine 5GHz link and real platform HFP.

## Finding 3 — it's D-SAM's own Bluetooth radio, not the phone's, and not about calls specifically

Reconnect D-SAM+D-POCO's stand-in link, then turn off **D-SAM's own** Bluetooth adapter (the
head unit side, not the phone's — toggling D-POCO's Bluetooth off does not stick, because
Android Auto keeps re-enabling it). With D-SAM's own Bluetooth off, a full call — ring,
answer, talk, hangup — produced zero disruption. Confirmed live via logcat during the call:

```
AapTransport: inbound rate over 30016ms: video=27kB/s (902 msgs), audio=62kB/s (1406 msgs)
VideoDecoder.logThroughput: rendered=150 (29fps) ... dropped=0
```

Video steady at 29-30fps and call audio actively streaming (62kB/s) through the same
WiFi/AAP session, with D-SAM's Bluetooth radio fully off. Confirmed again after hangup —
still steady, no drop.

Beyond avoiding the disconnect, this shows **call audio is not routed over Bluetooth
SCO/HFP in this wireless setup — it rides the WiFi/AAP channel**, the same way media audio
already does. Bluetooth's only job here is the initial wake/handshake/credential exchange;
it is not needed for call audio once the WiFi session is up.

Likely mechanism: D-SAM's own BT and WiFi radios contend for shared hardware/timing (common
on older/cheaper combo chips), and keeping the stand-in AT-command RFCOMM link open —
amplified by the extra chatter a real phone call triggers — starves D-SAM's own WiFi Direct
link badly enough to kill the TCP session outright.

**Caveat on rigor:** a first attempt to reproduce this by toggling *D-POCO's* Bluetooth
(instead of D-SAM's) via `adb -s <poco> shell svc bluetooth disable` failed to even place a
call — the session had already died from an unrelated `WiFi read timeout (15000ms)`
twelve-plus seconds before the toggle, and no call ever registered on either phone's telecom
log. That attempt is not evidence either way. Finding 3 rests on two hand-run tests toggling
D-SAM's own radio, not a scripted, controlled A/B — worth tightening before it drives a
design decision.

## Proposed direction (not implemented — needs its own design pass, not a hotfix)

Branch the hold behavior on whether the device has real Bluetooth-HFP-client capability:

- **No real platform HFP client (the stand-in path, e.g. D-SAM):** behave like a wireless
  AA dongle — connect via Bluetooth briefly for the wake + credential handoff, then drop the
  link once the WiFi session lands. Holding it open buys nothing here (call audio already
  rides WiFi) and costs stability on this class of hardware.
- **Real platform HFP client (D-HU and similar):** keep the persistent hold — this is
  exactly what `native-aa-hold-bluetooth-channel` already implements and what round 1
  verified clean (`hold-aa-rfcomm-round1-results.md`).

Open items for whoever picks this up: a clean scripted A/B isolating D-SAM's own BT on/off
with everything else held constant; confirming `stand-in` vs `real HFP client` is reliably
detectable where `HfpServiceRecordPolicy` decides to publish the record; confirming the
"call audio rides WiFi/AAP" behavior generalizes beyond this one phone/Gearhead version/call.
