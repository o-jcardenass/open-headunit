# station-scan, round 1 brief

A measurement round, not a fix. Nothing here is expected to PASS or FAIL a candidate; each run answers a question, and an honest "no" is as useful as a "yes".

## 1. Build and baseline

- Probe build: branch `probe/local-only-hotspot` on the fork, SHA **`b887adeb`** (`main` `d9e51925` plus one commit). It never merges.
  ```bash
  git fetch fork probe/local-only-hotspot
  git checkout b887adeb
  ```
  JVM suite on that SHA: 2374 tests, 0 failures.
- What the probe adds: one exported receiver, `connection.wifi.modes.nativeaa.LocalOnlyHotspotProbeReceiver`. It starts or stops a `LocalOnlyHotspot`, logs every station scan while it holds one, and writes the hotspot's name and password into `hotspot-ssid` / `hotspot-password` so the Native **Hotspot** transport hands them to the phone. Nothing else changes.

## 2. What this is and why it exists

Two reporters' tablets (Samsung, Android 11 and Android 16) lose audio every two to four minutes on Native AA over WiFi Direct. In both captures each stutter lines up with the head unit's **own** WiFi station scan. The station is on but joined to nothing, and from Android 11 the platform then scans every band, DFS included, on a 20/40/80/160 s backoff that stays at 160 s. That scan does not care whether a WiFi Direct group is running. On the worse unit, every inbound stream and the 1 Hz Ping stopped together for 3.3 s and 6.8 s, each time with the capture's only `StationScanMonitor` line inside the gap. Android 4.4 skipped these scans while a P2P group was connected, and Android 11 does not.

No public API stops the scan while the station is on. The one candidate lever is taking the station down entirely: on a unit that cannot run a station and an access point at once, a SoftAP should stop client mode, and its scanning with it. The app can start one itself with the public `LocalOnlyHotspot`. This round measures, per chip family:

- whether an unjoined station hurts a live session on this rig at all (D-POCO has never been reported to stutter);
- whether a `LocalOnlyHotspot` stops the station's scans;
- whether a real Native session runs over one, and on which band.

## 3. What is different about this round

- **This rig cannot reproduce the fault itself.** D-HU's UNISOC chip already takes these scans without a stall (section 8), and nothing here uses the reporters' Samsung chip. So the round measures whether the lever takes the station down and whether a session survives it. Whether it cures the stutter is graded on the reporters' tablets.
- **Units and staging.** D-MOTO is the phone for every run.
  - Stage A: D-POCO as head unit, Qualcomm.
  - Stage B: D-HU, UNISOC MT50, Android 14.
  - Stage C: D-SAM, Android 4.4.2. It has no `LocalOnlyHotspot` (API 26+), so it only runs R5.
- **Verify the station's state yourself before each run; never assume it.** Read `adb shell cmd wifi status` (API 30+) or `adb shell dumpsys wifi | grep -m3 -iE "mWifiInfo|curState"`, and record it. "Unjoined" means no `SSID:` / `Wifi is connected to` line.
- **Unjoining a station that holds a saved network.** List the network with `adb shell cmd wifi list-networks` and record it. Remove it with `adb shell cmd wifi forget-network <id>`. Restore it at the end of the stage with `adb shell cmd wifi connect-network <ssid> wpa2 <password>`. **If you do not have the password, stop and escalate**: forgetting a network you cannot restore is destructive.
- **A `LocalOnlyHotspot` needs location permission and location on.**
  ```bash
  adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
  adb shell pm grant $PKG android.permission.NEARBY_WIFI_DEVICES
  adb shell cmd location set-location-enabled true
  ```
  The second grant is refused below API 33; that is fine. If the start fails, the probe logs `LohsProbe: start failed, reason=` or `startLocalOnlyHotspot threw`. Record it: it is a result, not a rig fault.
- **Band is read from `dumpsys`, never from the app** (§7a):
  ```bash
  adb shell dumpsys wifi | grep -iE "SoftApInfo|mCurrentSoftApInfo" | head -3
  ```
  A 2.4 GHz hotspot is known to drop 1080p sessions on this route, so R4 on 2.4 GHz is expected to fail on throughput. Record the frequency and move on.
- **Music.** Start playback on the phone through the head unit with `adb shell input keyevent KEYCODE_MEDIA_PLAY` once projection is up (§3). Any stream with continuous audio will do.
- **Log level INFO** (`log-level=2`). Every line this round needs prints at INFO, and INFO keeps the ring buffer from wrapping over 10 minutes.
- The probe's receiver is **not** `AutomationReceiver`, so it needs its own helper:
  ```bash
  PKG=com.andrerinas.headunitrevived
  PROBE=$PKG/com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.LocalOnlyHotspotProbeReceiver
  probe() { adb shell am broadcast -f 0x00000020 -n $PROBE -a com.andrerinas.openheadunit.$1; }
  ```
  Every probe broadcast prints `LohsProbe: com.andrerinas.openheadunit.PROBE_LOHS_...` at INFO. Without that line, the step never landed.

## 4. Settings

With the app stopped (§1):

| Run | Key | Element |
|---|---|---|
| all | `log-level` | `<int name="log-level" value="2" />` |
| all | `wifi-connection-mode` | `<int name="wifi-connection-mode" value="3" />` |
| R1, R5 | `native-ap-transport` | `<int name="native-ap-transport" value="0" />` (WiFi Direct) |
| R4 | `native-ap-transport` | `<int name="native-ap-transport" value="1" />` (Hotspot) |
| R4 | `auto-enable-hotspot` | `<boolean name="auto-enable-hotspot" value="false" />` |
| R4 | `hotspot-ssid`, `hotspot-password` | delete both before the run; the probe writes them |

Record `stand-down-station-mode` as found. Do not change it.

## 5. Runs

Each run follows §4's clean-run protocol, with an unfiltered capture started before the launch and markers from `send ACTION_LOG_MARKER --es text <Rn-step>`.

### R0. Identity

`send ACTION_QUERY_STATE` on each head unit. `commit` must begin `b887adeb`.

### R1. Unjoined station on WiFi Direct, D-POCO (answers "why not D-POCO")

1. Unjoin the station (§3), confirm, then launch and arm: `send ACTION_START_WIRELESS_SCAN`.
2. Wait for `SSL handshake complete`, start music, and run **10 minutes**.
3. `send ACTION_DISCONNECT`.

Then **R1b**: the same 10 minutes with the station joined to the house network, as the control.

### R3. LocalOnlyHotspot with no session (the point of the round), D-POCO then D-HU

1. With the station unjoined and the app launched (`am start -n $PKG/com.andrerinas.openheadunit.main.MainActivity`), let it sit for **6 minutes** and count `StationScanMonitor` lines. This is the positive control: the same unit, the same state, no hotspot.
2. `probe PROBE_LOHS_START`. Record the `LohsProbe: started ...` line in full, `dumpsys wifi` SoftApInfo, and `cmd wifi status`.
3. Hold for **6 minutes**, logging nothing by hand. The probe prints a heartbeat every 30 s and a line for every scan.
4. `probe PROBE_LOHS_STOP`.

### R4. A Native session over the LocalOnlyHotspot, D-POCO then D-HU

1. Hotspot settings (§4), app launched, then `probe PROBE_LOHS_START`.
2. Wait for `LohsProbe: handed '` and then `send ACTION_START_WIRELESS_SCAN`.
3. Wait up to 240 s for `SSL handshake complete`, start music, and run **10 minutes**.
4. `send ACTION_DISCONNECT`, then `probe PROBE_LOHS_STOP`.

If the SoftApInfo frequency is below 5000, run only until the first `Throughput over` line after SSL, record it and the band, and stop.

### R5. KitKat control, D-SAM

R1 on D-SAM, with the station unjoined and WiFi Direct, for **10 minutes**. Before the arm, sit **3 minutes** with the app launched and no session, and count `StationScanMonitor` lines there too.

## 6. The lines that decide it

Verbatim from the source on `b887adeb`:

```
StationScanMonitor: station scans: N in Mms, ...                 a station scan window closing (INFO)
AapTransport: inbound link quiet N time(s) in Mms: dead=...      every inbound stream silent (INFO)
audio sink AUDIO over Mms: underruns=N, silentCycles=N, ... depth=...ms (min ...ms)   (INFO)
Throughput over Mms: rendered=N (Nfps), ...                      (INFO)
LohsProbe: started at +Ns ssid=... pskLength=N bssid=... bandMask=... staApConcurrency=..., station wifiEnabled=... supplicant=...
LohsProbe: station scan #N at +Ns (updated=...), station ...
LohsProbe: heartbeat +Ns, scans=N, held=..., station ...
LohsProbe: the platform stopped the hotspot at +Ns
LohsProbe: start failed, reason=N (...)
```

`StationScanMonitor` summarises a window only when the next scan arrives more than 30 s after the window opened. For timing, the probe's per-scan line is the better instrument; during R1 use the `StationScanMonitor` lines.

## 7. What each run answers

- **R1.** Scan count over 10 minutes, and for each scan whether an `inbound link quiet` line, an audio window with `underruns>0` or `min` depth under 100 ms, or a `Throughput` dip below 45 fps lands within ±5 s of it. **This is a measurement, not a grade.** Report "N scans, M coincided with a stall" per unit. R1b's count against R1's is the joined-vs-unjoined answer.
- **R3.** The lever works on a unit **only if** step 1 counted at least one scan in 6 minutes **and** step 3 counted **zero** probe scan lines with `held=true` throughout. If step 1 counted none, this unit cannot answer (the control never fired): say INCONCLUSIVE, not PASS. Report `staApConcurrency` alongside, since a `true` there predicts the station keeps scanning.
- **R4.** Report whether the session reached SSL and a picture, the band, fps over the 10 minutes, scan count, link-quiet count and underruns. A useful result needs 5 GHz, SSL, and zero scans.
- **R5.** Scan count during the session against the 3-minute idle count. Zero during a connected group, beside a non-zero idle count, measures KitKat's suppression.

## 8. Do not re-run

- **An unjoined station on D-HU over WiFi Direct.** `link-stall-periodic-scan-round5-results.md` R1 and R3 already measured it: 14 scans in 16.6 minutes, zero stalls over 1.2 s, zero `inbound link quiet`, zero underruns. UNISOC absorbs the scans, which is why D-HU gets only R3 and R4 here.

## 9. Report back

1. One table row per run and unit: station state as read, scan count, window length, stalls within ±5 s of a scan, and underruns.
2. For R3 and R4: the `LohsProbe: started` line verbatim, SoftApInfo verbatim, `cmd wifi status` verbatim, and `staApConcurrency`.
3. Whether the `LohsProbe` credentials survived a second `PROBE_LOHS_START` after a stop (same SSID or a new one). Run it once at the end of R3 on each unit.

Captures: `station-scan-round1-captures.zip` on release `rig-evidence-station-scan`, logcats only.
