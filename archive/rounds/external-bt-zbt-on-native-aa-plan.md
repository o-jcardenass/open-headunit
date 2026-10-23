# Re-port `feat/external-bt-zbt-probe` onto `feat/native-aa-wireless-and-bt-lifecycle`

Not a hardware round. This is a coding-session handoff: the plan for moving the external-Bluetooth /
ZLink-module transport (the `external-bt-zbt` thread's shipped code) onto the current native-AA
feature branch, so the #706 reporter can test the combined build and the transport is not stranded
on a v3.2.4 base. No APK, no rig. Whoever picks this up executes it in the code repo.

## Context

Issue #706: head units whose phone bonds to a vendor Bluetooth chip that `android.bluetooth` never
exposes, so the Native AA RFCOMM handshake never reaches the phone and Native wireless is refused
outright. `fork/feat/external-bt-zbt-probe` is the way around it — talk the vendor `gocsdk_zj` /
ZLink daemon's 16-byte-framed protocol on `127.0.0.1:3152`, prove it answers (a user-run probe in
Settings), and carry the whole AA Bluetooth handshake over that module. Background and the static
teardown that led here: `zlink-wpp-channel-brief.md`, `-results.md`, `-response.md` on this branch.

`fork/feat/external-bt-zbt-probe` is **4 commits on `a8830caad`** (~v3.2.4, 2026-08-12):

| SHA | Subject |
|---|---|
| `0606ebb56` | Speak the vendor Bluetooth daemon's protocol, and prove it answers |
| `a3c94582b` | Name what the handshake needs from its transport (`HandshakeLink`) |
| `9a0170fde` | Decide the route, and when to try, in tested places (`ExternalBtTransportPolicy`) |
| `f36f5c650` | Run Android Auto over the module when this unit is on that route (`ZbtAaCarrier`) |

Target: `fork/feat/native-aa-wireless-and-bt-lifecycle` @ `bb1e3e4e4` (current `main` + 21 commits,
heading toward a PR).

## Why a `git rebase` is not the tool

`git rebase external-bt-zbt-probe --onto feat/native-aa-wireless-and-bt-lifecycle` replays 4 commits
across **237 intervening commits** (216 of them in the `a8830caad..main` gap), and that gap contains
a full package reorganisation:

- `connection/NativeAaHandshakeManager.kt` -> `connection/wifi/modes/nativeaa/NativeAaHandshakeManager.kt`,
  rewritten ~1000+ lines on each side. The 4 zbt commits restructure 220 lines of it
  (`BluetoothSocket` -> a new `HandshakeLink` interface threaded through `handleHandshake` /
  `ifOwner` / `refuseWhileBackedOff` / `activeHandshakeSocket`, plus `zbtCarrier`,
  `startOverExternalModule()`, poke/stop branches).
- The mode-3 gate the zbt `AapService.kt` hunk edits was **moved out of `AapService.initWifiMode()`
  into `WifiLauncherNative.start()`**.
- `Wpp{Framing,HandshakeSession,Messages,Status}` and `NativeHandoffPolicy` moved from `aap/` to
  `connection/wifi/modes/nativeaa/` — the new `ZbtProbe` / `ZbtAaCarrier` import them by the old path.
- The first-gen external-BT override the zbt branch refactors
  (`manualSecondaryBluetoothServiceName` / `externalBtOverridden`) was independently renamed on the
  target branch to a `nativeAaIgnoreExternalBt` boolean.
- `SettingsFragment.kt` (+1131 gap / +223 branch vs +188 zbt), `Settings.kt`, `values/strings.xml`
  and 20 translation files all conflict.

Nearly every hunk conflicts and the result still would not compile (moved packages). Not worth it.

## Approach: manual re-port as fresh commits

New branch off `fork/feat/native-aa-wireless-and-bt-lifecycle`, e.g.
`feat/external-bt-zbt-probe-on-native-aa`. The pure-logic files transfer nearly verbatim (only
imports change); only ~4 integration points need real work. Use the original 4 commits' diffs as the
hunk-by-hunk reference — the branch's `NativeAaHandshakeManager` still has the same structural shape
(`activeHandshakeSocket`, `ifOwner(socket)`, `handleHandshake(socket, localRadio)`,
`refuseWhileBackedOff(socket)`, `start()` / `triggerPoke()` / `manualPoke()` / `stop()`), so each zbt
transformation maps 1:1.

**Settings model decision: the route enum supersedes.** Replace the branch's `nativeAaIgnoreExternalBt`
boolean with `ExternalBtTransportPolicy.Route { NORMAL, ZBT, BLOCKED }`, derived from `ExternalBtPolicy`
evidence + one `externalBtZbtTransport` toggle. ZBT => module carrier; BLOCKED => today's
refuse-outright; NORMAL => default. This is exactly what the zbt branch's `transportRoute()` already
does.

## Files and steps

### 1. Pure-logic files — copy from `fork/feat/external-bt-zbt-probe`, fix imports only

Sources (`app/src/main/java/com/andrerinas/openheadunit/`):

- `connection/zbt/ZbtByteChannel.kt`, `ZbtFraming.kt`, `ZbtMessages.kt`, `ZbtProbe.kt`,
  `ZbtAttemptPolicy.kt`, `ZbtWakePolicy.kt`, `ZbtAaCarrier.kt`
- `connection/HandshakeLink.kt` (interface + `BluetoothSocketLink`) — stays in package `connection`,
  valid on both sides.
- `connection/wifi/modes/nativeaa/ExternalBtTransportPolicy.kt` — **new location** (zbt had it in
  `aap/`); put it next to the `ExternalBtPolicy` usage in the nativeaa package.

Tests (`app/src/test/java/com/andrerinas/openheadunit/`): `connection/zbt/ZbtByteChannelTest.kt`,
`ZbtFramingTest.kt`, `ZbtMessagesTest.kt`, `ZbtProbeTest.kt`, `ZbtAttemptPolicyTest.kt`,
`ZbtWakePolicyTest.kt`, and `.../nativeaa/ExternalBtTransportPolicyTest.kt`.

Import fixups (grep the copied files and rewrite):

- `com.andrerinas.openheadunit.aap.Wpp*` -> `com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.Wpp*`
- `com.andrerinas.openheadunit.aap.NativeHandoffPolicy` ->
  `com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.NativeHandoffPolicy`
- `ExternalBtTransportPolicy` package line + its importers.

### 2. `utils/Settings.kt`

- Delete `nativeAaIgnoreExternalBt` (pref key `native-aa-ignore-external-bt`).
- Add `externalBtZbtTransport: Boolean` (pref key `external-bt-zbt-transport`, default `false`) —
  verbatim from the zbt diff, keeping its comment about talking to a vendor daemon on a
  disassembly-recovered protocol.
- No migration: both default false, semantics changed, `nativeAaIgnoreExternalBt` only existed on the
  unreleased branch.

### 3. `connection/wifi/modes/nativeaa/NativeAaHandshakeManager.kt` — apply the zbt transformation

Reference: `git show a3c94582b 9a0170fde f36f5c650 -- .../NativeAaHandshakeManager.kt`.

- Companion: replace `externalBtOverridden(context)` with
  `transportRoute(context): ExternalBtTransportPolicy.Route` (feeds `BluetoothHelper.externalBtEvidence`
  and `settings.externalBtZbtTransport` into `ExternalBtTransportPolicy.route(...)`). Keep
  `externalBtDiagnostic()`.
- `checkCompatibility()`: `if (transportRoute(context) == Route.ZBT) { AppLog.i(...); return true }`
  before the existing checks.
- Thread `HandshakeLink` in place of `BluetoothSocket` (~15 sites, mechanical):
  `activeHandshakeSocket` -> `activeHandshakeLink`, `ifOwner(link)`, `refuseWhileBackedOff(link)`,
  `handleHandshake(link)`; wrap both `accept()` results in `BluetoothSocketLink(socket, radioName)`;
  read identity/streams via `link.peerName / peerAddress / radioLabel / persistPeerForAutoStart /
  input / output`. Keep the auto-start-MAC save behind `link.persistPeerForAutoStart`.
- Add `@Volatile private var zbtCarrier: ZbtAaCarrier? = null`.
- Add `startOverExternalModule()` (sets `isRunning = true`, `aaListenersClosedForSession = false`,
  builds `ZbtAaCarrier` with the same closures as the zbt version —
  `NativeHandoffPolicy.shouldServeHandshake`, `commManager.isConnected`, `isHandoffSettling()`,
  `isHandshakeInFlight()`, all present on branch — and launches `carrier.run()` on `scope`).
- `start()`: `when (transportRoute(context)) { ZBT -> { startOverExternalModule(); return }; BLOCKED
  -> { diagnostic; return }; NORMAL -> Unit }` before the existing SDK/adapter setup.
- `triggerPoke()` and `manualPoke()`: early `zbtCarrier?.let { it.requestWake(); return }`
  (manualPoke also `resetHandshakeBackoff()` first, per zbt).
- `stop()`: `zbtCarrier?.close(); zbtCarrier = null` alongside the existing `activeHandshake*`
  teardown.

### 4. `connection/wifi/modes/WifiLauncherNative.kt` — the moved mode-3 gate (~line 63)

Replace the `externalBtDiagnostic()` / `!externalBtOverridden(service)` / `if (!blockedByExternalBt)`
block with a `transportRoute(service)` check: run the WiFi half + `handshakeManager?.start()` unless
the route is `BLOCKED`; log the diagnostic only on `BLOCKED`. `ZBT` takes the same WiFi path as
`NORMAL` — `handshakeManager.start()` dispatches to the carrier internally.

### 5. `main/SettingsFragment.kt` — merge the two UIs onto the route model

Reference: `git show 0606ebb56 f36f5c650 -- .../SettingsFragment.kt`.

- Remove the branch's `pendingNativeAaIgnoreExternalBt` (field + ~4 assignment sites + the toggle row
  near line 1135 + the `checkChanges` comparison).
- Add from zbt: `pendingExternalBtZbtTransport`; the `zbtProbe*` companion members (`zbtProbeJob`,
  `zbtProbeScope` = `CoroutineScope(SupervisorJob() + Dispatchers.IO)`, `zbtProbeResult`);
  `confirmAndRunZbtProbe()`, `startZbtProbe()`, `followZbtProbe()`; the `onResume` "pick the probe
  row back up" hook; the two rows (toggle `externalBtZbtTransport` + action `zbtProbe`) gated on
  `BluetoothHelper.externalBtEvidence != null`, placed **outside** the Native-AA-mode block
  (reporters are on units where that mode is refused).
- Merge `showNativeAaExternalBtDialog` (~line 3388): keep the branch's `MaterialAlertDialogBuilder`
  dialog, drive the `viaModule` wording from `settings.externalBtZbtTransport`
  (`external_bt_module_nativeaa` / `_desc` when on, `external_bt_nativeaa` / `_desc` when off).
- Add imports: `CoroutineScope`, `Job`, `SupervisorJob`, `delay`, `isActive`, `ZbtProbe`.

### 6. `res/values/strings.xml` (+ translations)

- Add `zbt_probe_{title,idle,running,message,watch_only,wake_and_watch}`, `external_bt_transport`,
  `external_bt_transport_description`, `external_bt_module_nativeaa`, `external_bt_module_nativeaa_desc`.
- Update `external_bt_nativeaa_desc` to the zbt wording (points at the probe).
- If `manual_secondary_bt_service_{title,message}` still exist on the branch, remove them from
  `values/` and the 20 `values-*/` files (verify first — the branch may already lack them, it never
  carried `manualSecondaryBluetoothServiceName`).

### 7. `utils/BluetoothHelper.kt`

Doc-comment-only tweak on `getAdapterHandleForService` (now unused, kept). Optional.

## Commit structure (mirror the original 4)

1. Zbt daemon protocol + probe + tests + the probe UI in `SettingsFragment`.
2. `HandshakeLink` + thread it through `NativeAaHandshakeManager`.
3. `ExternalBtTransportPolicy` route enum (supersedes `nativeAaIgnoreExternalBt`) + `transportRoute()`
   + `WifiLauncherNative` gate + `Settings` key swap + strings/translations.
4. `ZbtAaCarrier` + `startOverExternalModule()` + poke/stop wiring.

## Verification

- `./gradlew :app:testGithubDebugUnitTest` — new `Zbt*` and `ExternalBtTransportPolicy` tests pass;
  existing `NativeAaHandshakeManagerTest`, `ExternalBtPolicyTest`, `NativeHandoffPolicyTest`,
  `Wpp*Test` still green.
- `./gradlew :app:assembleGithubDebug` — compiles. Main risk is stale imports from the reorg:
  `grep -rn "openheadunit\.aap\.Wpp\|connection\.NativeAaHandshakeManager\|openheadunit\.aap\.ExternalBtTransportPolicy" app/src`
  should return nothing.
- On-rig regression (MT50, single BT radio, no external module => ZBT route is INCONCLUSIVE there):
  confirm `NORMAL` route still forms a Native AA session, and an external-BT unit still gets the
  refusal dialog.
- The meaningful ZBT test is the #706 reporter's unit — build an APK from commit 4 for them and ask
  for a Verbose log covering a probe run (both "Watch only" and "Wake and watch") plus a connection
  attempt with the toggle on.
- Confirm `Settings.exporterLogLevel` default is `INFO` on this base.

## Follow-up

The new branch is standalone so the reporter can test now. When
`feat/native-aa-wireless-and-bt-lifecycle` merges, fold these 4 commits into it (or rebase onto the
merge result — trivial once the bases match) before opening the #706 PR.
