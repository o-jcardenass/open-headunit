# home-widgets-opacity, round 3 results

- Candidate: `a2a3b0d71d8b9be2e058408bc4fc9a35bbc7c514` (tree `a9c7d363b88deb6559ae922332cdbbc3def014b6`), `fork/feat/home-widgets-and-opacity`
- Baseline: none (each run's control is the role holder read before it)
- APK md5: C `1ccf9a56c689bc5fef688b9937923a51`; `apk_identity` exit 0 and `commit":"a2a3b0d71d8b"` on both units
- Units: D-HU (API 34, en-US, root), D-SAM (API 19)
- Date: 2026-10-27

## Round verdict: FAIL

L1 (a point of the round) FAIL, L2 (the other point) PASS, L3 UNTESTABLE (brief erratum), L4 PASS, L5 PASS.

## Setup notes

- Quirk files read: `tooling.md`, `units/D-HU.md`, `units/D-SAM.md`.
- Opening pre-flight: D_HU wifi 1, bt 1, Awake, HFP none, A2DP none; D_SAM wifi 1, bt 1, Awake; rig clean, lock free. No radio changed. HU uid 0, locale en-US, sdk 34; D-SAM sdk 19. Backups: D-HU 10293 B, D-SAM 8045 B. `HU_HOME=com.android.launcher3`, `HU_OWNER=u0_a176:u0_a176`.
- Build gate: tree matched; `HomeRoleRequestPolicy`=3, `openPath`=1.
- The host ran the brief's session scripts directly (`hu.sh`, `sam.sh`), not through `rig-executor`, because the brief ships complete scripts with in-script checks. Injected taps are the brief's own (at most 3 per run, each resolved from a dump by id or text).
- Host thermal: max 68C, throttle 0 through both sessions. `running-after-stop` read 0 before every run.
- **Brief erratum (L3):** the Save button id is `:id/settingsSave`; the brief names `save_button_widget`, which does not exist in dumps `ui/L3-a.xml` or `ui/L3-b.xml`. The tap printed `NO_UNIQUE_NODE (matches=0)`, so L3 is void per the brief. Not re-run with the corrected id.
- `sam.sh` printed `script exit=143`: the last command is `th_end`'s `wait` on the killed thermal watch. Markers `L5-start`/`L5-end` are in the capture; not a failure.
- **Restore:** `hu holder final` = `com.android.launcher3` (HU_HOME). `restore.sh` printed `SETTINGS_DIFFER` for both units. The diff is app-written WiFi Direct bookkeeping from the restore launch (BSSID, group SSID, passphrase, PSK digest, group-name counter 161 to 162 on D-SAM) plus key order; no `enable-car-launcher`, `connection-modes` or `log-level` value differs. Not retried.
- **Closing pre-flight:** first run FAILED on one idle Gradle daemon (stray); `rig_cleanup.sh kill` removed it (permitted case), then D_HU and D_SAM both `PREFLIGHT OK`, wifi 1, bt 1, Awake, rig clean, lock free (`closing-preflight.txt`). Radios match the opening table.
- L4's `add-role-holder` reset was redundant: L3 had already left the holder as `com.android.launcher3`.

## L1

**FAIL**

The dialog appeared and the grant went through, but launcher mode was not stored (fix 2's point).

| Item | Value |
|---|---|
| `holder-before` | `com.android.launcher3` |
| `switch-before` | `false` |
| Taps | `settingSwitch` (00:52:12), `Open Headunit` (00:52:18), `Set as default` (00:52:24), all resolved |
| `asked the system for the home role` | 1 (00:52:10.220) |
| `Package name cannot be null or empty` | 0 |
| `home role granted after` | **0** (PASS needs 1) |
| `car launcher mode stored` | **0** |
| `with no answer, opening the Home settings` | 0 |
| `holder-after` | `com.andrerinas.headunitrevived` |
| `key-live` / `key-after` | `value="false"` / `value="false"` (PASS needs `true`) |
| `fatal` | 0 |

Decisive lines in the window:

- `00:52:10.187 CarLauncherManager: set launcher alias enabled=true`
- `00:52:10.220 CarLauncherManager: asked the system for the home role`
- `00:52:22.334 RequestRoleFragment: Application added as a role holder, role: android.app.role.HOME, package: com.andrerinas.headunitrevived`
- `00:52:22.333 ActivityTaskManager START ... category.HOME ... cmp=.../CarLauncherAlias`, then `00:52:22.416 MainActivity.logLaunchSource: App launched by third party: android-app://com.android.permissioncontroller`
- `00:52:22.626 MainActivity: the app is the home app with launcher mode not stored yet, keeping the launcher alias`

Reading: the system granted the role and immediately started the Home activity (the alias), which replaced the settings screen. `onRoleRequestResult` never ran, so nothing stored the key (the 10 s wait for the granted line timed out). The state is the one the fix targets (role held, key `false`), so fix 2's grant path did not close it; only the open path (L2) did.

## L2

**PASS**

| Item | Value |
|---|---|
| `holder-before` / `holder-after` | `com.andrerinas.headunitrevived` both |
| `SettingsFragment: the app holds the home role` | 1 |
| `switch` | `true` |
| `set launcher alias enabled=false` | 0 |
| `key-after` | `value="true"` |
| `fatal` | 0 |

## L3

**UNTESTABLE**

The Save tap did not resolve (brief erratum above). Observed, not graded: after the switch tap alone, `set launcher alias enabled=false` logged once and the holder read `com.android.launcher3` straight away (`holder-after-save`, `holder-before-reopen`), so the role dropped at once and the key stayed `true` (nothing saved). `reopen-window storing` 0; `switch-reopen` read `NONE` (the reopen dump showed no switch). This does not answer the known-risk question for the save-then-reopen order.

## L4

**PASS**

| Item | Value |
|---|---|
| `asked the system for the home role` | 1 |
| `home role not granted after` | 1, **6706 ms** (over the 1000 ms floor) |
| `with no answer, opening the Home settings` | 0 |
| `resumed` | `com.andrerinas.headunitrevived/...main.SettingsActivity` |
| `holder-after` | `com.android.launcher3` |
| `fatal` | 0 |

## L5

**PASS**

| Item | Value |
|---|---|
| `asked the system for the home role` | 0 |
| `resumed` | `android/com.android.internal.app.ResolverActivity` (the Home chooser) |
| `fatal` | 0 |

Screenshot `ui/L5-b.png` is in the captures zip.

## Anything the brief did not ask about

- After L1's grant, `MainActivity` itself logged "the app is the home app with launcher mode not stored yet, keeping the launcher alias", so the app recognises the state L1 left behind. The next opening of the settings screen is what stores the key (L2).
- The fixed `Package name cannot be null or empty` fault did not occur (0 in L1 and L4); the dialog was shown both times.
- D-HU's role dialog took about 12 s from request (00:52:10) to the recorded grant (00:52:22) with 2 taps.
- **Evidence:** release asset `home-widgets-opacity-round3-captures.zip` on `rig-evidence-home-widgets-opacity`, sha256 `0fe42c9520db26d82abc669c0723d0b4dbea05f6a7ab300f9f2b759565868c82` (C.apk excluded, md5 above).
