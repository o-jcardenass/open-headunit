# session-reconnect, round 3 addendum: run the round on the merged stack build

**Read this before `pr-1047-session-reconnect-round3-brief.md`. It replaces that brief's section 1 (build) and section 1a (identity gate) only.** Sections 2 to 9 stay as written: the same runs (R0, A0, N2, N2R if needed, ND, U0, U1), the same taps, the same settings, the same decisive lines and the same results file name.

## Why

The stack round (`pr-stack-1047-1067-round1-results.md`) ran the contributor's five PRs merged together, tree `2c4b06fd`. That build contains this PR's code, but no stack run made a settings Save or sent a Disconnect. So N2, ND and U1 are still open. The stack is the code that will land, and #1064 and #1065 change `CommManager.kt` and `AapService.kt` beside this PR's Save path. So this round measures the Save on the stack, not on this PR alone. `main` stays at `77914f18`.

## 1. Build (replaces section 1)

| Arm | Code | Identity | Gate |
|---|---|---|---|
| **C** (candidate) | the stack: PR 1067 head `e4116c17df8c505c3d3db8235d56f6cfe408805f` with PR 1064 head `e6b249051a8610b717b78bf2fe6aaf95b56410cf` and PR 1065 head `abd732c3d12c959949f53f9df4398b690ec66fe6` merged in. It contains PR 1047 head `7cc36de8212368c4e0099bb286a36362b44cfee8` and PR 1045 head `dbe0004d4794d029082994cc2e9ee5a5aa93d3c2`. | tree `2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad` | 2995 JVM tests, 0 failures (our run on this tree); do not run them on the rig |
| **E** (export build, Stage U only) | C plus `android:exported="true"` on `.main.SettingsActivity` | C's tree plus that line | not run; one XML attribute |

**Reuse first.** If the stack round's local branch `arm-S` still exists, its tree prints `2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad`, and you kept a copy of its APK with md5 `ed9dc2c9b9f2f17f04a6a69305c53274`, that APK is C. Skip the C build and record the reuse in Setup notes. Build only E.

Otherwise build C. Cool the host to 70C first, build with `GRADLE_OPTS=-Dorg.gradle.workers.max=2`, and copy each APK out of `apks/` the moment it is built.

```bash
for p in 1045 1047 1064 1065 1067; do git ls-remote https://github.com/andreknieriem/open-headunit.git pull/$p/head; done
# MUST print, in order: dbe0004d..., 7cc36de8..., e6b24905..., abd732c3..., e4116c17...
git fetch https://github.com/andreknieriem/open-headunit.git main pull/1064/head pull/1065/head pull/1067/head
git checkout -B arm-C e4116c17df8c505c3d3db8235d56f6cfe408805f
git merge-base --is-ancestor 7cc36de8212368c4e0099bb286a36362b44cfee8 HEAD || echo "1047 NOT IN STACK"
git -c user.name=rig -c user.email=rig@local merge -q --no-edit e6b249051a8610b717b78bf2fe6aaf95b56410cf abd732c3d12c959949f53f9df4398b690ec66fe6
git rev-parse 'HEAD^{tree}'                        # MUST print 2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad
# E: one attribute on top of C (or on top of arm-S if you reuse it), committed locally only
git checkout -B arm-E arm-C
sed -i 's|android:name=".main.SettingsActivity"|android:name=".main.SettingsActivity"\n            android:exported="true"|' app/src/main/AndroidManifest.xml
git diff --stat                                    # MUST print 1 file changed, 1 insertion(+)
git -c user.name=rig -c user.email=rig@local commit -qam "rig: export SettingsActivity"
```

The first merge prints `Auto-merging .../AapTransport.kt`. That is expected and clean. If a scratch worktree has no `local.properties`, copy the main worktree's file in before the build.

**Stop the round and escalate if** any head differs from the list, `1047 NOT IN STACK` prints, a merge conflicts, or the tree differs. The merge commits and `arm-E` stay local: do not push them.

## 1a. Identity gate, per APK, before any run on it (replaces section 1a)

```bash
adb -s $HU shell pm path $PKG                 # pull that apk to ./installed.apk, then:
for s in 'SettingsRestart: route=' AapMessageReassembler ConnectionAdmissionRejectedException AutomaticReconnect 'encrypted write failed or incomplete'; do
  printf '%s\t' "$s"; unzip -p installed.apk 'classes*.dex' | strings | grep -cF "$s"; done
```

Each count must be 1 or more, on C and on E. The stack round read 1, 13, 2, 10, 1.

On E only, also: `aapt2 dump xmltree --file AndroidManifest.xml installed.apk | grep -A4 'main.SettingsActivity' | grep -c 'exported.*0xffffffff'` must print 1.

Send `ACTION_QUERY_STATE` and record `commit`. It must equal `git rev-parse --short=12 HEAD` of the arm that built that APK. The merge commit is local, so its SHA is yours, not a fixed one; the reused `arm-S` APK reads `4f10e12578b8`. Record each APK md5 from a real `adb pull` plus a local `md5sum`. C and E must differ.

## What else changes

- **Section 5's lines are unchanged.** We checked every unit line in section 5 with `grep -F` against `app/src/main` and `contract/src` of the stack tree. Each one prints the same number of times as in the PR's own tree `a13a8e49`, including the composed `SettingsRestart:` lines. Where section 5 or the results skeleton names the C tree `a13a8e49`, read `2c4b06fd`.
- **The settings screen, its layout and the manifest are identical in both trees.** The stack changes nothing under `main/`, `res/` or the manifest, so `tap_save`'s two targets, the deep link and the export `sed` work as written.
- **R0:** replace the brief's "2901" and "CI on `7cc36de8`" with "2995 JVM tests, 0 failures, our run on tree `2c4b06fd`". Everything else in R0 stands.
- **Results skeleton:** write the candidate line as `Candidate C: the stack, tree <printed vs 2c4b06fd2c77cc8a25bcb88f775b5d51ec12d1ad>, local merge commit <sha>`.
- **D-POCO already has the stack APK installed** (`ed9dc2c9`), left there by the stack round. Stage U installs E over it with `adb install -r -d`. Never uninstall.

## Rig rules from the stack round

- **Count logcat readers before and after every run.** `ps aux | grep -c "[l]ogcat"` must print 0 before a run starts and after it ends. In the stack round, two readers outlived `cap_stop`, ran for 20 minutes and voided a run. If the count is not 0, kill the readers by pid, record it, and void the run if they ran during it.
- **Kill the build's Gradle daemon before the pre-flight** (`rig_cleanup.sh kill`). It fails `rig_preflight.sh` otherwise.
- **D-POCO can be left in airplane mode by an earlier script.** Before Stage A and Stage U, read airplane mode, WiFi and Bluetooth on D-POCO, and restore them if needed.
- **Check that the dongle enumerated before U0 starts.** `adb -s $POCO shell ls /sys/bus/usb/devices` must show more than the two root hubs. If not, ask the operator to reseat it, once.

## Time

About 55 min, or about 45 min if the C APK is reused. Stage A is unattended. Stage U needs the operator for the cables only.
