# ultrawide-touch-alignment: round 1 brief

Host: the **MT50 as the head unit** (panel **1440x720**, aspect 2.0), phone as usual. Read
`TESTING-TEMPLATE.md` before planning any step. §7a, known rig quirks, applies in full.

This file is append-only. Corrections arrive as new commits and a `git pull` fast-forwards, so no
brief you have already read changes under you.

Read `ultrawide-touch-and-usb-pr943-findings.md` on this branch first. It carries the measured root
cause and the arithmetic this round is checking. **The fault reproduces on our own rig**, so this
round needs no reporter hardware.

---

## 1. Build and baseline

```bash
git fetch fork
git checkout fix/video-fit-and-ultrawide-touch    # 371477b6
```

| Input | SHA | Role |
|---|---|---|
| `main` | `6cd70399` | **the baseline arm.** Build this too, it is half the round |
| `fix/video-fit-and-ultrawide-touch` | `371477b6` | **the candidate.** 2 commits on `main` |

This round has a **real baseline APK**: `main` itself. The candidate branches directly off it and
nothing else is in the tree, so the A/B is clean and no testing merge branch is needed.

Unit gate on the candidate: **1445 / 0** (`testGithubDebugUnitTest`). On `main`: **1414 / 0**. Any
other count means the wrong tree was built; stop and say so rather than running the round.

## 2. Drive it with the automation surface

The command surface is **on `main` now**, so both arms of this round have it and neither needs the
settings UI or a hand-edited `shared_prefs/settings.xml` for anything except one bootstrap flag.
Use it for every step below.

```bash
PKG=com.andrerinas.headunitrevived
RCV=$PKG/com.andrerinas.openheadunit.automation.AutomationReceiver
A=com.andrerinas.openheadunit
send() { adb shell am broadcast -a "$A.$1" "${@:2}" -n $RCV; }
```

`am broadcast` sends an ordered broadcast and prints the receiver's JSON reply as `data=`, so every
command below is self-verifying. Read the reply rather than assuming a command landed.

**Bootstrap, once per install.** The configuring verbs are gated on "Allow external configuration",
and that flag cannot turn itself on through the surface it gates. Write it once, app stopped, per §1
of the template:

```bash
adb shell am force-stop $PKG
# insert <boolean name="allow-external-configuration" value="true" /> before </map>
```

A test-APK install resets settings through onboarding, so **redo this after every install** and
confirm it before the first run of each arm. A `Refuse` reply saying external configuration is off
is what a forgotten bootstrap looks like.

### Exact APK identity, and the two builds

```bash
send ACTION_QUERY_STATE
```

The reply carries `commit` (the build's `GIT_SHA`), `versionName`, `flavor`, `connected` and
`state`. **That is the APK fingerprint for this round**; record it in Setup notes for every run and
stop if it is not the SHA the run is supposed to be on. It is exact, unlike an md5 of a rebuilt APK.

Two log lines are **new in the candidate and absent from the baseline**, which is the second way to
tell the arms apart:

```
[ServiceDiscovery] PixelAspectRatioE4 is: N (10000 = square)     INFO,    candidate only
[UI_DEBUG] Touch map: raw=X,Y -> video=X,Y view=WxH ...          VERBOSE, candidate only
```

The baseline arm therefore cannot report a PAR or a touch mapping. That is expected: on the baseline
you measure the **scale line only**, which exists in both builds:

```
[UI_DEBUG] Normal Scale. scaleX: N, scaleY: N                    INFO, both
[ServiceDiscovery] NegotiatedResolution is: WxH                  INFO, both
[ServiceDiscovery] Margins are: WxH                              INFO, both
[RES_CAP] resolutionId=N ... chosen=X capped=Y                   INFO, both
```

### The three commands every run uses

```bash
send ACTION_SET_LOG_LEVEL --es level verbose
send ACTION_SET_SETTINGS  --es json '{"settings":{"resolutionId":2}}'
send ACTION_LOG_MARKER    --es text "R2 start"
```

`ACTION_LOG_MARKER` is deliberately **not** gated, so it works before the bootstrap flag is on. Put
a marker before and after every run: it is what separates runs inside one capture, and this round
has six of them.

`ACTION_SET_SETTINGS` takes the backup format, an object under a `settings` key. Its reply lists
`imported`, `skipped` and `restartNeeded`. **A key in `skipped` was not applied**, so check that
field rather than trusting the command returned ok.

## 3. Settings this round sets

| Key | Type | Value | Meaning |
|---|---|---|---|
| `resolutionId` | int | `2` | force 720p. `3` is 1080p, used only by R4 |
| `video-fit-mode` | int | `0` FILL, `1` CONTAIN, `2` COVER | candidate only; absent means FILL |
| `pixel-aspect-ratio-e4` | int | `10000` | anything else is a manual override and suppresses the derivation |

`resolutionId` values come from the settings enum: `0` Auto, `1` 480p, `2` 720p, `3` 1080p.

**`pixel-aspect-ratio-e4` is the trap in this round.** A value other than 10000 always wins, so if a
previous run or onboarding left one behind, R2 measures nothing and looks like a plain failure. Read
it back with `send ACTION_GET_SETTINGS` before every run rather than assuming.

Log level goes through `ACTION_SET_LOG_LEVEL` (`verbose`), not the settings import. The touch line
does not print below Verbose.

---

## 4. The runs

Marker, run, marker. Every run: `send ACTION_LOG_MARKER --es text "<run> start"` first, the same with
`end` after.

### R0, build gate

Both trees build and `testGithubDebugUnitTest` passes: candidate **1445 / 0**, baseline **1414 / 0**.
No device needed. A failure here ends the round.

### R1, baseline at 720p

Baseline APK, `resolutionId=2`, Verbose. Connect and project.

**Expect** `scaleY: 1.125` on the `Normal Scale` line, with `scaleX: 1.0`. Capture the
`NegotiatedResolution`, `Margins are` and `RES_CAP` lines with it.

PASS if `scaleY` reads 1.125. If it reads 1.0 the fault did not arm, and the likeliest cause is that
the resolution did not land at 720p. Check `RES_CAP` before concluding anything.

While you are on this arm, also capture one **1080p** session (`resolutionId=3`) so R4 has something
to compare against. That is a settings change and a reconnect, not a separate build.

### R2, candidate at 720p

Candidate APK, same settings, `video-fit-mode` absent or `0`.

**Expect** `scaleY: 1.0` and `scaleX: 1.0`, and
`[ServiceDiscovery] PixelAspectRatioE4 is: 11250`.

PASS needs both. Report them separately: the scale fix and the PAR derivation are different commits
and either can fail alone.

### R3, touch alignment, the point of the round

Candidate, still 720p. **Do not eyeball this.** Drive it with `input tap` and read the mapping back:

```bash
adb shell input tap X Y
adb logcat -d | grep "UI_DEBUG] Touch map"
```

Tap at least these five points and record the `raw=` and `video=` pair for each. For a 1440x720
panel at 720p in FILL with no margins, the mapping is a plain proportional scale:

| tap (raw) | expected video |
|---|---|
| 40,40 | about 36,40 |
| 1400,40 | about 1244,40 |
| 40,680 | about 36,680 |
| 1400,680 | about 1244,680 |
| 720,360 | about 640,360 |

x scales by 1280/1440 = 0.889; y is 1:1 because the negotiated height equals the panel height.

PASS if every point is within a few pixels of the table and, in particular, **y comes back equal to
the raw y**. A y multiplied by roughly 1.125, or an x off by a constant, is the failure this round
exists to catch.

The **baseline half of R3 is observational and says so in the results.** That build has no touch
line, so the only thing available there is tapping a known Android Auto control and recording
whether it activates. Do not let a verdict rest on it.

### R4, regression guard at 1080p

Candidate, `resolutionId=3`. This is the rig's normal mode and the case that already worked.

**Expect** margins `480x360`, `PixelAspectRatioE4 is: 10000`, and the scale line unchanged from the
1080p capture taken in R1.

PASS if the PAR is 10000 and the margins are 480x360. A PAR of 11250 here means the derivation is
measuring the raw negotiated size instead of the margin-reduced canvas, and is a real defect.

### R5, CONTAIN

Candidate, 720p, `video-fit-mode=1`. One connect, one look, one set of taps.

**Expect** `scaleX` about 0.889 and `scaleY: 1.0`, a visible pillarbox left and right, no distortion,
and taps still landing. Note whether the bars are where you expect.

### R6, COVER

Candidate, 720p, `video-fit-mode=2`.

**Expect** `scaleX: 1.0` and `scaleY: 1.125`, no bars, the top and bottom of the picture cropped, no
distortion, and taps still landing.

R5 and R6 are one run each. They check that the modes behave and that touch follows them; they are
not hunting for anything.

---

## 5. What this round cannot answer

Whether Android Auto's **layout** actually changes at a derived PAR, rather than the picture merely
being un-stretched. The rig's 11250 is a mild correction; the reporter's unit derives 15000, which is
where a layout change would be obvious. If R2 passes on the numbers but the picture still looks wrong
in a way the scale line does not explain, that is a question for the reporter's hardware, not one to
guess at here.

Also out of scope, and recorded in the findings file rather than tested: every renderer scales about
the view centre while the margin the phone honours is an edge crop, so `getTopMargin()` and its three
siblings are computed and read by nothing.

## 6. Reporting

Fixed format, per §7 of the template. Put in Setup notes: the `commit` from `ACTION_QUERY_STATE` for
every run, the `pixel-aspect-ratio-e4` read-back before each run, the `skipped` list from any
`ACTION_SET_SETTINGS` that did not import cleanly, and any run where the resolution did not land
where the brief asked.
