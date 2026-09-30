# hp-slate-bringup — round 1 brief (mini)

## 1. Build and baseline

Candidate only, no baseline APK needed, no fix under test.

```bash
git fetch fork
git checkout main   # use whatever HEAD is at round time
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

This is a bring-up round for a device that has never run a scripted round before, not a
verification of a code change. R0 is still the usual build+test gate.

## 2. What this is and why it exists

A fourth device, `CNU350BGBJ` (HP Slate 7 Plus, codename `birch`, Android 4.2.2 / API 17, not
rooted), has been added to the device table. Prior probing on this unit already found Native AA
(mode 3, WiFi Direct) and the hotspot strategy both permanently blocked at the driver/OEM level, so
its only viable role is **USB head unit**: the tablet as USB host, a real phone plugged in as an
AOAP accessory. Call it **D-HP** for the rest of this brief.

Two problems came up trying to get evidence off this device, plus one open question:

- **The in-app "Export Logs" button either fails or its result is unverified on D-HP.** On every
  other rig device this is a manual UI tap (no exported intent) that calls
  `LogExporter.saveLogToPublicFile`; on at least one modern ROM that call has ANR'd when `logcat`
  was gated behind a consent dialog. D-HP predates that consent mechanism entirely (it is Android
  12+ only), so if the tap fails here the mechanism is different and needs its own capture, not an
  assumption that it is the same bug.
- **We need the fallback in place regardless**: a full `adb logcat` capture taken alongside a real
  USB-connected session, so a round on D-HP has usable evidence even if the in-app export stays
  broken.
- **`show-fps-counter` has never been confirmed to render on D-HP.** It reads `Temp:`/`Frame:` text
  in the corner on every other device tested (see `ultrawide-touch-alignment-round5-results.md`);
  whether it draws at all on this GPU/API combination is unknown.

This round does not fix anything. It produces the diagnostic evidence (capture + screenshot) the
next planning pass needs to decide whether either problem is a real defect.

## 3. What is different about this round — D-HP-specific facts, verify none have drifted

- **Never `adb reboot` D-HP.** A prior reboot froze it hard enough to need a manual power-cycle,
  cause unconfirmed. If a radio needs resetting use `svc wifi disable`/`svc wifi enable`, not a
  reboot. If a hard freeze happens anyway, stop the round and escalate rather than trying to
  recover it yourself.
- **Not rooted (uid 2000 shell).** Use the rig's `run-as`-based pref writer
  (`set_hu_settings_runas.py` or equivalent — inventory `hur-wifi-test-scripts/` first per house
  rule 1), the same pattern already used for the POCO. Never the root-only host writer.
- **`SettingsActivity` is not reachable with `am start -n` on this device** — it throws
  `SecurityException` from the shell uid here specifically (it works fine on the other three
  devices). Launch `com.andrerinas.openheadunit.main.MainActivity` instead and navigate in-app to
  Settings, then use the in-app "Search settings" field to reach the row you need, exactly as
  `log-and-selfmode-fixes-round1-results.md` R2 did. Log every tap in Setup notes.
- **`WRITE_EXTERNAL_STORAGE` is a normal install-time permission on API 17** — no runtime grant
  screen exists, so if the export still fails it is not a permission prompt you missed.
- **Read the exported file with plain `adb shell`, not `run-as`.** `run-as`'s relative `files/` cwd
  is a different, internal-storage path; the real one is
  `/storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/`.
- Package `com.andrerinas.headunitrevived`. No Google/Play Store path exists on this unit; not
  relevant to this round.
- A real phone is needed as the USB accessory. Use the POCO X3 NFC (`4f4027e9`) — plug it into
  D-HP via the OTG side, D-HP is the host.
- **D-HP has exactly one USB port and no wireless adb.** adb-to-PC and the phone-as-OTG-accessory
  are mutually exclusive: whichever is plugged in, the other cannot be. This rules out a live,
  host-tethered `adb logcat` and a live `adb exec-out screencap` during any USB-accessory session —
  there is no window where both an adb session and the phone are attached at once. R2 and R3 are
  written around that: R2 uses the app's own on-device capture (`log-capture-enabled`), which a
  daemon thread writes straight to disk with no PC involved (confirmed on another device in
  `post-beta1-latency-instruments-round2-results.md` R7 — the segments are complete even when the
  export UI path failed), retrieved after reconnecting adb. R3 substitutes a physical photo of the
  screen for the screenshot, the same way the `ultrawide-touch-alignment` rounds do when adb can't
  reach the display. Both need one operator-attended cable swap; that is expected here, not a
  round-invalidating deviation — say so in Setup notes rather than treating it as one.
- **OTG host mode likely does not charge D-HP.** Budget the untethered window accordingly (a few
  minutes, not an extended soak) and check battery level before starting it.

## 4. Settings keys this round needs

| Key | Type | Value | When |
|---|---|---|---|
| `log-level` | int | `0` (VERBOSE) | all runs |
| `log-capture-enabled` | boolean | `true` | R1, R2 |
| `show-fps-counter` | boolean | `true` | R3 |
| `view-mode` | int | leave at standing value for the first R3 pass; sweep `0`/`1`/`2` (SURFACE/TEXTURE/GLES) only if the overlay is absent at the standing value | R3 |

Back up `settings.xml` before writing anything and restore it (same pushed-script pattern) at the
end of the round.

## 5. This round is diagnostic, not pass/fail against a known line

Nobody has a decisive log line for either problem yet — that is what this round produces. Don't
grep for a specific string and call a miss a FAIL; capture broadly and report what is actually
there; a Sonnet pass can grep `-iE "logexporter|fps|overlay|exception|crash|anr"` case-insensitively
over the capture and hand back matches with timestamps, but a human (the Opus judgement step) reads
any exception/stack trace found before this round's verdicts are written.

## 6. Runs

### R0 — build + unit tests (gate)

Standard gate. **PASS** required to continue.

### R1 — Export Logs button on D-HP

Force-stop, write settings (§4), relaunch via `MainActivity`. Start
`stdbuf -oL adb logcat -v time > r1.txt &` **before** the relaunch, per the standing capture
protocol — this is the safety net regardless of what the button does. Navigate in-app to Settings
via Search ("Export"), tap the "Export Logs" row once. Wait up to 30s.

**PASS:** a `HUR_Log_*.txt` appears under
`/storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/` and is non-empty (report
its size and the `LogExporter: session |` banner line from inside it).
**FAIL:** the app hangs/ANRs, crashes, or the row does nothing after 30s — attach the full `r1.txt`
capture (never an excerpt) and any `Application Not Responding` / stack trace lines it contains.

### R2 + R3 — one untethered USB-accessory window (on-device capture + a photo)

These share a single cable-swap cycle since the port only allows one attachment at a time.

**While adb is still attached:** force-stop, write `log-capture-enabled=true`, `log-level=0`,
`show-fps-counter=true` (§4), note battery level. Relaunch via `MainActivity`. Confirm (still
tethered) that `settings.xml` read back correctly and that the app is running.

**Cable swap:** unplug the adb cable, plug in the POCO over USB-OTG. Let the accessory flow run —
system chooser dialog, permission dialog, connection attempt — for at least 3 minutes past whatever
state it settles into. If a session forms, look for the FPS HUD in the corner and take a photo of
the physical screen with it clearly framed (this is R3's evidence — a photo, not a scripted
capture, per the port constraint above). Photograph any dialog too if one is on screen and stuck.

**Cable swap back:** unplug the phone, reconnect adb. Pull the on-device capture (R2's evidence):

```bash
adb shell ls -la /storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/
adb pull /storage/emulated/0/Android/data/com.andrerinas.headunitrevived/files/<each HUR_Log_*.txt>
```

Use plain `adb shell`/`adb pull` against that absolute path, not `run-as` (its relative `files/`
cwd is a different, internal-storage path — §3).

**R2 PASS:** at least one non-empty `HUR_Log_*.txt` covering the untethered window is retrieved
this way (report size and its `LogExporter: session |` banner line), showing on-device capture is a
usable evidence path on this device with no PC attached. Report how far the USB connection itself
got (formed session / stuck on a dialog / error) as a fact, not part of the verdict.
**R2 INCONCLUSIVE:** only if no segment file exists at all for a reason unrelated to the USB
connection (e.g. `log-capture-enabled` didn't take) — say why, and confirm the settings readback
from before the cable swap.

**R3 PASS:** the photo shows the HUD (`Temp:`/`Frame:` legible or not, report which).
**R3 FAIL:** the photo shows no HUD. If absent, a `view-mode` sweep (0/1/2) is optional follow-up,
not required for this mini round — each mode needs its own full cable-swap cycle (adb-attached
settings write, swap to phone, reform session, photo, swap back), so only do it if time allows and
say in Setup notes how many modes were actually tried. Whether or not the sweep runs, grep the R2
segment files for anything matching `-iE "fps|overlay"` near session start and report any hits with
timestamps — a lead for the coding session, not something this round needs to resolve.

## 7. Report back

One file, `hp-slate-bringup-round1-results.md`, following TESTING-TEMPLATE.md §7 exactly. The three
numbers that matter: did Export Logs produce a usable file (R1), is a plain logcat capture a
reliable substitute on this device (R2), and is the FPS overlay ever visible here and does it
depend on view-mode (R3). Evidence — `r1.txt` and the pulled `HUR_Log_*.txt` segments — goes in
`evidence/hp-slate-bringup-round1/`; the R3 photo(s) go in a sibling
`hp-slate-bringup-round1-photos/` next to the results file, per the `ultrawide-touch-alignment`
convention.
