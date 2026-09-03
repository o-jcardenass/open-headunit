# PR readiness — round 1 results

**Candidate:** one combined APK from `fork/testing/automation-plus-btautostart` @ `e9ab7f13`
(= `fork/pr/bt-auto-start` `99eb41a7` with `fork/pr/automation-command-surface` `773e8e77`
merged `--no-ff`; both PR tips are ancestors of `e9ab7f13`, verified with
`git merge-base --is-ancestor`). **Baseline:** none (no A/B in this brief).
**APK md5:** `0abee20c530f234033508c8d63c7769d` (single APK, `3.3.0` / versionCode 103, `github/debug`,
`commit=e9ab7f135ec7`)
**Unit:** D-HU = UNISOC MT50 (`MT50_YT610E4GFPSL_U`, Android 14, 1440x720), joined to `Pegue Cdesta`
5260 MHz throughout. D-POCO = POCO X3 NFC (Android 15, Gearhead 17.5.663204) — projecting phone for
§4, Self Mode head unit for §5.
**Date:** 2026-09-02

## Setup notes

- **One APK, not two.** The brief specifies APK A (`pr/bt-auto-start`) and APK B
  (`pr/automation-command-surface`). Per the user's instruction this round used a single merged
  testing branch (`testing/automation-plus-btautostart`) carrying all three PRs, so that §6's
  automation commands are available to drive §4/§5. One merge conflict, in
  `AapService.observeConnectionState()`: the bt-auto-start stack added `sessionConnectedAt =` to the
  `Connected` arm, the automation branch added a `Connecting ->` arm plus `emitSessionState(...)`
  calls — resolved by keeping all three. Nothing else conflicted.
- **R0 gate (adapted to the single APK):**
  - `assembleGithubDebug` clean; `testGithubDebugUnitTest` **1247 / 0** (superset of the brief's
    A 1223 / B 1010; includes `BtAutoDisconnectPolicyTest`, `AutomationCommandPolicyTest`,
    `AutomationOutputPolicyTest`).
  - DEX symbols, all `> 0`: `createClaimed` 1, `CredentialFreshnessPolicy` 5, `BtAutoDisconnectPolicy`
    3, `AutomationCommandPolicy` 27, `AutomationOutputPolicy` 10, `SessionStateIntent` 3,
    `AutomationReceiver` 5.
- **6.1's SHA match.** The brief wants the `ACTION_QUERY_STATE` reply's `commit` to equal
  `773e8e77`. It reports `e9ab7f135ec7` — the merge commit that *contains* `773e8e77`. The intent of
  6.1 (a build the log can be tied to) is met; the literal value differs only because this is the
  combined testing branch, not `pr/automation-command-surface` alone.
- **§5 ran after the operator enabled D-POCO's Android Auto developer "Start head unit server"
  (`:5277`)** (not adb-startable — `DeveloperHeadUnitNetworkService` is not an exported/known
  component). The combined APK was installed on D-POCO (`adb install -r -d`, `settings.xml`
  preserved, md5 `0abee20c…`) and Self Mode configured. **PASS** — details under R5.
- **D-POCO's OHU was upgraded** from `190488f9…` to the combined APK for §5 and **left installed**
  (prior-round convention); D-POCO `settings.xml` restored byte-identical afterwards.
- **Plain `MainActivity` launch does not start a Self Mode session on this config** (needs
  `auto-start-self-mode=true` or an explicit trigger). The first §5 session was formed by the
  auto-start trigger itself (a D-HU BT off/on cycle → `MATCH!` → `forcing a Self Mode launch`), then
  aged and dropped again for the auto-disconnect proper — the same shape as `bt-auto-start-disconnect`
  round 1's R6.
- **Scripts:** added `hur-wifi-test-scripts/pr_readiness_s4.sh` (one §4 bring-up) and
  `pr_readiness_s5.sh` (§5 auto-disconnect smoke). Used `set_hu_prefs.sh` for all D-HU settings
  writes; D-POCO settings edited on the host and pushed via `run-as` (Python inline, no `.bak`
  next to `settings.xml` — that path gets rolled back by Android). §6 commands were sent with plain
  `adb shell am broadcast`.
- `grep -a` used on every capture per §7a. Captures streamed with `stdbuf -oL`, filtered to
  `OPENHU:V` (+ `ActivityManager:I` for §6) to stay under the rig's logcat-flood stall threshold.
- D-HU `settings.xml` restored **byte-identical** (md5 `4870ea90b9667ae1774cd564b54dfb9a` before and
  after), `allow-external-configuration` and `stand-down-station-for-wifi-direct` keys absent again,
  `auto-start-bt-macs` back to `DC:B7:2E:5E:4E:59`, D-HU rejoined `Pegue Cdesta`, both devices'
  radios on, the one file written in 6.5 deleted. **D-POCO `settings.xml` restored byte-identical**
  (md5 `d291974fe3f3c2f51b2eb3b43c2aea28`), `connection-modes`/`wifi-connection-mode=3`/
  `auto-start-bt-macs` all back to their originals. Gearhead never force-stopped; `:5277` still
  listening at round end. Combined APK left installed on D-HU and D-POCO.

---

## R4 (§4) — one bring-up chain through the station stand-down — **PASS, 3 / 3**

- Settings written (D-HU): `wifi-connection-mode=3`, `stand-down-station-for-wifi-direct=true`,
  `wifi-5ghz-channel=36`, `static-bssid=0`, `native-wifi-version-exchange=false`, `log-level=1`.
- Radio state: D-HU joined to `Pegue Cdesta` 5260 MHz before every iteration (verified each time;
  the stand-down restored the association between iterations via autojoin). D-POCO radios taken
  fully down during each HU cold launch, brought back after the ~22 s settle.
- Discard-rule check: clean on all three (`createGroup SUCCESS` = 1 each; `p2p-wlan0-N` advances by
  exactly 1 per iteration: `-0`, `-1`, `-2`). One `MATCH! Starting AapService` per iteration (two on
  iter2) — all lone phone-BT-reconnect MATCHes with zero group churn attached, benign per §7a.

Per-iteration counts:

| | `startNativeAaQuietHost() requested` | `5GHz createGroup SUCCESS!` | claim line present | `StationStandDown … has left` | session (`WirelessServer: Incoming` + SSL) | `p2p-wlan0` | `reported N off/on state changes` |
|---|---|---|---|---|---|---|---|
| iter1 | **1** | **1** | yes | **1** | yes | `-0` | **0** |
| iter2 | **1** | **1** | yes | **1** | yes | `-1` | **0** |
| iter3 | **1** | **1** | yes | **1** | yes | `-2` | **0** |

Decisive sequence, iter1 (iter2/iter3 identical in shape):

```
13:32:51.969  claimNativeCreateWindow | a Native AA group create is claimed (waiting for this unit to leave its own network)
13:32:52.170  1.onReceive             | WIFI_P2P_STATE_CHANGED_ACTION state=2      <- P2P-enabled receiver; started NO chain
13:32:53.486  StationStandDown        | this unit has left its WiFi network.
13:32:53.488  claimNativeCreateWindow | a Native AA group create is claimed (bringing the Native AA group up)
13:32:53.490  startNativeAaQuietHost  | startNativeAaQuietHost() requested. Removing old group if any...   <- the ONLY chain
13:32:53.491  claimNativeCreateWindow | a Native AA group create is claimed (recreating the group)
13:32:54.042  1.onSuccess             | 5GHz createGroup SUCCESS!
13:33:16.552  WirelessServer          | Incoming connection detected from /192.168.49.54
13:33:16.852  AapSslContext           | SSL handshake complete
```

The last round's R6 failure was two `startNativeAaQuietHost()` chains — one from the
`WIFI_P2P_STATE_CHANGED state=2` receiver, one from the stand-down completion callback — and two
`createGroup SUCCESS`. Here the claim is staked at `:51.969`, *before* the P2P-enabled broadcast at
`:52.170`, so `P2pStateChangePolicy.shouldStartBringUp`'s new `createClaimed` arm sees the claim and
the receiver defers. Only the stand-down callback's chain runs, at `:53.490`, after the stand-down
finished at `:53.486`. One chain, one group, per iteration. **The R6 regression is fixed.**

The `reported N off/on state changes … no request of ours outstanding` warning is **absent** on all
three, and no stack-cycled banner appeared — the stand-down's own P2P cycling is correctly stamped
as ours, no false accusation.

### Noticed, not asked

- `disableNetwork()` returned **false** on every iteration (the platform refused it), yet
  `wm.disconnect()` still took the station off its network and the 5 GHz group formed cleanly — the
  stand-down reaches its goal on this rig despite the API refusal.
- The stand-down **restore** also logs `the platform refused to re-enable this unit's WiFi network.
  It may have to be reconnected by hand.` every iteration, but D-HU re-associated with `Pegue
  Cdesta` on its own (autojoin) within the inter-iteration gap each time — no manual tap was needed
  this round, unlike the `p2p-bringup-loop` rounds.

---

## R5 (§5) — the auto-disconnect still fires (smoke) — **PASS**

- Settings written (D-POCO): `connection-modes={self}`, `wifi-connection-mode=1`,
  `auto-start-bt-macs={11:46:03:10:33:59}`, `auto-disconnect-bt-macs={11:46:03:10:33:59}`
  (D-HU's real adapter MAC, read from the rooted HU as `11:46:03:10:33:59` = the masked
  `XX:XX:XX:XX:33:59` "Navegadortz2" in D-POCO's bonded list), `kill-on-disconnect=false`.
  Overlay op `allow`. `:5277` listening (operator-enabled).
- Radio state: D-POCO Bluetooth cycled at the start to (re)establish the link to D-HU; D-HU adapter
  toggled with `svc bluetooth disable` (self-reverts ~14 s on this rig, §7a).
- Discard-rule check: not applicable (Self Mode, no P2P group). One capture, `s5_poco.txt`.

The first drop landed with no session up (plain `MainActivity` launch does not auto-start Self
Mode here), so the first cycle only re-armed. A session then formed by that re-arm; it was aged
~79 s and D-HU's adapter dropped again — the run the brief asks for:

```
14:03:07.361  SSL handshake complete                    ← session 1 (formed by the earlier re-arm), then aged
14:04:25      svc bluetooth disable on D-HU              (session live, age ~79 s > 60 s guard)
14:04:26.992  Bluetooth auto-disconnect: 11:46:03:10:33:59 went away; ending the session in 5000ms unless it comes back.
14:04:31.996  Bluetooth auto-disconnect: 11:46:03:10:33:59 stayed away; ending the session the way the Exit button does.
14:04:32.006  session state disconnected (user_exit)
14:04:32.121  Self Mode disconnected. Not restarting.
14:04:33.204  Bluetooth auto-disconnect: keeping the wireless bring-up down until something re-arms it.
14:04:37.773  BT Device connected: Navegadortz2 (11:46:03:10:33:59)   ← D-HU adapter self-reverted
14:04:37.773  MATCH! Starting AapService via Bluetooth Auto-start...
14:04:37.811  MainActivity: Bluetooth auto-start: forcing a Self Mode launch
14:04:37.872  SelfMode: AA 17.4+ detected. Connecting directly to Headunit Server on 127.0.0.1:5277...
14:04:38.599  SSL handshake complete                    ← session 2 (fresh, by the re-arm)
14:04:40.165  session state projecting
14:04:40.335  session state disconnected (user_exit)    ← the harness's headunit://exit teardown
```

All four brief conditions met: the `went away; ending the session in 5000ms` line, then
`stayed away; ending the session the way the Exit button does.`, the session ends, and the adapter
self-reverting produces `MATCH! Starting AapService` and a fresh session (SSL at `14:04:38.599`).
The 60 s age guard (`not ending the session for … (up=…, age=…)`) did **not** fire — the session
was old enough — count 0. `kill-on-disconnect=false`, so the OHU pid was not killed. PR 2 is also
byte-identical to the `bt-auto-start-disconnect` round-1 stack (R6/R7 PASS end to end there).

---

## R6 (§6) — the automation command surface — **6.1–6.7 all PASS**

All on D-HU. App launched once (`MainActivity`) so `App.provide()` / `AppComponent` exists.

### 6.1 Build identity — PASS

```
am broadcast -n <RX> -a com.andrerinas.openheadunit.ACTION_QUERY_STATE
→ result=0 data={"action":"…ACTION_QUERY_STATE","versionName":"3.3.0","versionCode":103,
   "commit":"e9ab7f135ec7","flavor":"github","connected":false,"wireless":false,
   "wifiMode":"NATIVE","state":"Disconnected","ok":true}
```

JSON, names the commit. `e9ab7f135ec7` contains `773e8e77` (see Setup notes).

### 6.2 Control commands — PASS

| command | reply |
|---|---|
| `ACTION_START_SELF_MODE` | `{"…":"…START_SELF_MODE","ok":true}` |
| `ACTION_QUERY_STATE` (#1) | `…"connected":true,"wireless":true,"wifiMode":"NATIVE","state":"TransportStarted","ok":true` |
| `ACTION_RAISE_PROJECTION` (brief's string) | **refused**: `"error":"unknown action com.andrerinas.openheadunit.ACTION_RAISE_PROJECTION"` |
| `com.andrerinas.openheadunit.aap.action.RAISE_PROJECTION` (correct string) | `{"…","ok":true}` — also `ok:true` via implicit broadcast |
| `ACTION_DISCONNECT` | `{"…","ok":true}` |
| `ACTION_QUERY_STATE` (#2) | `…"connected":false,"wireless":false,"state":"Disconnected","ok":true` |

Session started (`state:TransportStarted`) and ended (`state:Disconnected`); the two state replies
differ as the run did. **Brief-shorthand error (not a code bug):** `raise projection`, `refresh
sensors` and `restart audio` are registered and recognised under
`com.andrerinas.openheadunit.aap.action.RAISE_PROJECTION` (etc.), not
`com.andrerinas.openheadunit.ACTION_RAISE_PROJECTION`. The brief's `$A.ACTION_RAISE_PROJECTION`
reaches the receiver (explicit `-n`) but `AutomationCommandPolicy` returns `unknown action`.

### 6.3 Configuration refused while the switch is off — PASS

`allow-external-configuration` key absent (default off). Both refused with a reason:

```
ACTION_GET_SETTINGS   → result=1 "error":"external configuration is off; turn on \"Allow external configuration\" in Settings"
ACTION_SET_LOG_LEVEL  → result=1 (same error)
```

`log-level` read back from `settings.xml` = `1`, unchanged.

### 6.4 An export withholds credentials — **PASS** (the run that matters)

Bait values written to `settings.xml` (switch on): `hotspot-password=LEAKTEST-PW-9931`,
`hotspot-ssid=LEAKTEST-SSID-4471`, `auto-start-wifi-ssid=LEAKTEST-WIFISSID-8823`,
`auto-start-bt-name=LEAKTEST-BTNAME-2210`, `auto-start-bt-macs={LEAKTEST-BTMAC-5566}`.

```
am broadcast -n <RX> -a com.andrerinas.openheadunit.ACTION_GET_SETTINGS
→ result=0, "settings":{…67 keys, none credential-bearing…}, "withheld":6, "ok":true
```

**Grep of the full reply for each literal bait value — 0 hits each:**

```
LEAKTEST-PW-9931        : 0
LEAKTEST-SSID-4471      : 0
LEAKTEST-WIFISSID-8823  : 0
LEAKTEST-BTNAME-2210    : 0
LEAKTEST-BTMAC-5566     : 0
```

None of `hotspot-password`, `hotspot-ssid`, `auto-start-wifi-ssid`, `auto-start-bt-macs`,
`auto-start-bt-name`, `static-bssid` appear as keys in the reply. `withheld` = 6 (> 0). No leak.

### 6.5 A write cannot choose where it lands — PASS

- `--es path /data/data/<pkg>/shared_prefs/settings.xml` → **refused**:
  `"… is not a directory this app may write to; use the app's Downloads or files directory"`.
  `settings.xml` md5 `8e258ca6…` identical before and after.
- `--es path /sdcard/Download/ohu-settings.json` → **refused** (same reason). The allowed-root check
  is a literal string prefix and does not canonicalize `/sdcard` → `/storage/emulated/0`, so this
  common alias is rejected. Fails **closed** — not a leak — but the brief's literal command needs
  `/storage/emulated/0/Download/`.
- `--es path /storage/emulated/0/Download/ohu-settings2.json` → **succeeds**,
  `"file":"/storage/emulated/0/Download/ohu-settings2.json","withheld":6`; file present (2045 bytes)
  and its contents also contain 0 bait values. Deleted at round end.

### 6.6 The session broadcast reports the session — PASS

Native AA session on D-HU (D-POCO connected back). `AapService: session state` log line, in order:

```
13:40:56.819  session state connecting
13:40:56.822  session state connected
13:40:57.712  session state projecting          (transport = wifi — QUERY_STATE showed wireless:true, wifiMode:NATIVE)
13:41:14.182  session state disconnected (user_exit)     ← after headunit://exit
```

`state` reaches `projecting` while AA is up; `headunit://exit` produces `disconnected` with
`reason=user_exit`. **Credential-absence:** `SessionStateIntent` carries exactly four extras by
construction — `state`, `transport` (bounded literal `self`/`wifi`/`usb`/`unknown`), `reason`,
`uptime_ms` — none able to hold an SSID, passphrase or phone name. Nothing on this rig registers for
`com.andrerinas.headunitrevived.SESSION_STATE`, so the non-sticky broadcast leaves no dumpable
extras record; the empirical grep for credentials in the extras could not be performed on hardware
and rests on `AutomationOutputPolicyTest` and the 4-field structure. A `dumpsys activity broadcasts`
snapshot during a live session showed no `SESSION_STATE` record carrying `Pegue Cdesta`,
`POCO X3 NFC`, the passphrase `9cufLb8Da9IW`, `DIRECT-`, or `192.168.49.*` (0 hits each).

### 6.7 The log marker — PASS (with the switch on)

```
am broadcast -n <RX> -a com.andrerinas.openheadunit.ACTION_LOG_MARKER --es text "round1-check"
→ result=0 "ok":true
09-02 13:44:07.832  W/OPENHU … AutomationEffectRunner.run | AutomationMarker: round1-check
```

At `W` (WARN), as specified. **Finding:** `ACTION_LOG_MARKER` is in the `CONFIGURING` set, so with
`allow-external-configuration` **off** it is refused (`"external configuration is off…"`). During
the un-gated part of a round (6.1, 6.2) the marker cannot be used to separate captures, contrary to
the brief's "use it to separate every run above". A marker neither reads nor writes state; consider
moving it out of `CONFIGURING`.

---

## Anything the brief did not ask about

- **`ACTION_START_SELF_MODE` on D-HU actually formed a Native AA session, not a Self Mode one**
  (`wireless:true`, `wifiMode:NATIVE`) — D-POCO was paired with radios up from §4 and reconnected
  over Bluetooth ACL. The command was accepted and a session started/ended as 6.2 requires, but on a
  rig with a live paired phone `START_SELF_MODE` is not a clean Self Mode trigger.
- The three `aap.action.*` verbs (`RAISE_PROJECTION`, `REFRESH_SENSORS`, `RESTART_AUDIO`) have a
  different action-string namespace from every other automation verb. Anyone scripting from the
  `ACTION_*` names in `HeadUnitCommand` will get `unknown action` for these three unless they read
  the constant values. Worth a note in `contract/README.md`, or aliasing the `ACTION_`-prefixed
  forms.
- `/sdcard/...` path alias rejected by 6.5's write guard (see 6.5) — minor, fail-safe.

## Shipping read

- **PR 1:** §4 PASS 3/3 — the station stand-down produces one bring-up chain and one group. The
  last round's only open failure is closed. PR-ready.
- **PR 2:** §5 PASS — the auto-disconnect ends a live aged Self Mode session on D-HU's Bluetooth
  going away, and the adapter self-reverting re-arms a fresh session. Also covered by the
  byte-identical `bt-auto-start-disconnect` round-1 R6/R7. PR-ready.
- **PR 3:** 6.1–6.7 all PASS, including the credential-withholding run (6.4) and the write-path
  guard (6.5). Two brief-example errors (the `aap.action.*` string, the `/sdcard` alias) and one
  finding (`ACTION_LOG_MARKER` gated by the config switch) — none blocks the PR; all three are
  worth a follow-up commit.

**All three pull requests are hardware-clear.**
