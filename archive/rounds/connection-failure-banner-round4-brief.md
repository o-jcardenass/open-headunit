# connection-failure-banner, round 4 brief: three surfaces that went away, and two that moved

**Candidate:** `fix/session-lifecycle-and-diagnostics` @ `7db4e0c6` on `fork`
(`o-jcardenass/open-headunit`). Head of draft PR #867. **No baseline.** One APK for the whole round.

```bash
git fetch fork --tags
git checkout -B fix/session-lifecycle-and-diagnostics fork/fix/session-lifecycle-and-diagnostics
git rev-parse HEAD          # must print 7db4e0c6...
git log --oneline -5
# 7db4e0c6 Wireless settings: let the user pick the band, and show each lever where it is read
# b03cb4ed Native AA: keep the reason a connection failed, and say it on the main screen
# 3213612d Native AA: one session at a time, and credentials the phone can use
# a960b9cf Offline VPN: give it an owner, a session lever, and a teardown that works
# 39d13272 Diagnostics: see a media-only outage, an idle screen, and the station state
```

## 0. Read this before the checkout: the history was rewritten

Round 3 measured `3ad29942`, and **that SHA is no longer on this branch**. It is not the tip and it
is not an ancestor of the tip. Two things happened after round 3's APK was built:

1. a commit removed the notification and the toast this thread's conditions used to raise;
2. the branch was compacted from twelve commits to five, grouped by component. The content is
   provably identical at the tip: the compaction was verified as an empty diff against the
   pre-compaction state.

Nothing round 3 measured has changed behaviour, and none of its verdicts is withdrawn. But **a plain
`git pull` will not work** on a checkout that still has the old history. Use the `checkout -B` line
above, or `git fetch fork && git reset --hard fork/fix/session-lifecycle-and-diagnostics`.

Tags pin what earlier rounds measured, so nothing is lost: `round2-candidate` is round 2's
`bd3d7b99`, `round3-candidate` is round 3's `3ad29942`, and `session-lifecycle-pre-compaction` is
the twelve-commit tip.

## 1. Why this round exists

Round 3 was R0-R8 all PASS, and the thread is going to PR. This round exists only for what has
changed since that APK was built. It is short and it is the last one before the PR.

**Three surfaces became one.** The three conditions this thread is about used to be reported three
ways at once: a log line, an Android notification on its own high-importance channel, and, for the
hotspot condition only, a toast forced past the user's own toast preference. The notification and
the toast both predate the main-screen banner, which is what round 3 spent its runs proving works.
The banner is durable, it names the remedy, it taps through to the row that fixes it, and the
settings-screen preflight asks the same questions before the user ever connects. So the notification
and the toast are gone. **Round 3 never saw either one**, because they were removed after its APK
was built, so R1 and R2 below are the first measurement of the removal in either direction.

What is emphatically **not** changing: the record, the banner, the log lines, the retire rules, and
everything R1 through R8 of round 3 confirmed. If any of that has moved, this round has found a
regression and that is the most valuable thing it can report.

**Two rows on the wireless screen changed.** Both band hints were long enough to be an argument
rather than help text, at 584 and 612 characters on a panel that is often 800x480. They are two
sentences each now. And the WiFi Direct band selector moved up: it used to sit below three rows that
are shared by both transports, and now sits directly under the transport selector, the way the
hotspot arm's own rows already did.

## 2. What is different about this round

- **The "Connection Setup" notification channel may still be listed on the rig.** An `adb install -r`
  does not remove a channel that a previously installed build registered, and round 3's APK
  registered `headunit_setup_needed`. Seeing it in the unit's notification settings is **expected and
  is not a FAIL**. What must not happen is a notification *appearing* on it. If you want it gone,
  uninstall rather than reinstall, but that also wipes `shared_prefs` and its ownership fix, so it is
  not worth it for this.
- **No settings change re-resolves anything**, exactly as in round 3. Every step is force-stop,
  write, relaunch.
- **The banner refreshes on `onResume()` only** (see `TESTING-TEMPLATE.md` §7a, added after round 3
  raised it). "Let the condition raise and confirm the banner" always means force-stop and relaunch.
- **Clear `connection-issue-dismissed-at` alongside any seed** (same section, same reason). R2 seeds
  nothing, but if you seed anything while diagnosing, clear it.
- The phone is needed for R2 only. Everything else is head-unit-side.

## 3. Settings keys this round needs

| Key | Type | Element | Why |
|---|---|---|---|
| `wifi-connection-mode` | int | `<int name="wifi-connection-mode" value="3" />` | Native AA throughout. |
| `native-ap-transport` | int | `<int name="native-ap-transport" value="1" />` | `1` = hotspot, `0` = WiFi Direct. Set it explicitly every time. |
| `hotspot-ssid` | string | `<string name="hotspot-ssid">OHU-TEST</string>` | **Deleted for R1.** The condition cannot raise with a name on file. |
| `hotspot-password` | string | `<string name="hotspot-password">testtest1234</string>` | Deleted for R1. |
| `static-bssid` | string | `<string name="static-bssid">AA:BB:CC:DD:EE:FF</string>` | Deleted for R2, or the BSSID condition cannot raise. |
| `log-level` | int | `<int name="log-level" value="1" />` | DEBUG, as in round 3. |
| `show-toast-messages` | bool | `<boolean name="show-toast-messages" value="true" />` | **R1 needs this ON.** The removed toast was forced past this setting, so leaving it off would make its absence prove nothing. |

Delete all four `connection-issue-*` keys before R1 and before R2.

## 4. The lines that decide every run

All copied from `7db4e0c6`. Use `grep -a`, always.

| Grep string | Level | Means |
|---|---|---|
| `SoftApCredentials: The access point on` | e | the hotspot condition raising by itself. R1's trigger. |
| `MainActivity: showing the connection issue banner for` | i | the banner went up, and the suffix names the condition. |
| `NativeAA: BSSID is still masked/empty` | e | the BSSID condition raising. R2's trigger. |
| `these credentials carry no passphrase` | w | the hotspot record raised at the publish site. |
| `CredentialsNotice` | any | **must be absent from every capture.** The class is deleted; any hit means the wrong APK. |

## 5. Runs

### R0 - build and unit gate

```bash
./gradlew :app:assembleGithubDebug
./gradlew :app:testGithubDebugUnitTest
```

**PASS:** clean build, suite green, **698** tests, unchanged from round 3. Nothing this round adds or
removes a test, so a different number is itself the finding. Report it either way.

### R1 - the hotspot condition raises with no notification and no toast

Round 3's R1, re-run for what is now absent. Force-stop. `wifi-connection-mode=3`,
`native-ap-transport=1`, `show-toast-messages=true`, `hotspot-ssid` and `hotspot-password` **deleted**,
all four `connection-issue-*` keys deleted. Bring the access point up and confirm it immediately
before launching, per §7a. Launch, watch the screen for the first 15 s, leave it 60 s.

Then force-stop, relaunch, and read the main screen.

**PASS, all five:**

- `SoftApCredentials: The access point on` at least 1, so the condition really did raise;
- `connection-issue-hotspot-config` is **non-zero** in `settings.xml`;
- the banner is on the main screen after the relaunch, with one
  `showing the connection issue banner for HOTSPOT_CONFIG_UNREADABLE`;
- **no notification appeared**, checked two ways: nothing from this app in the shade during the 60 s,
  and `adb shell dumpsys notification --noredact | grep -i headunitrevived` shows no posted
  notification other than the ongoing foreground-service one ("Open Headunit");
- **no toast appeared** in the first 15 s. `show-toast-messages` is on, so this is a real check.

A screenshot of the shade during the 60 s and one of the banner after the relaunch.

### R2 - the BSSID condition raises with no notification

Round 3's R4 setup, minus the seed. Force-stop. `wifi-connection-mode=3`, `native-ap-transport=0`
(WiFi Direct), `static-bssid` **deleted**, all four `connection-issue-*` keys deleted. Location
services **off**, which is what makes the address unreadable. Launch, let the phone reach the
handshake, wait for the abort.

**PASS, all four:**

- `NativeAA: BSSID is still masked/empty` at least 1;
- `connection-issue-bssid` is non-zero afterwards;
- after a force-stop and relaunch, the banner names `BSSID_UNAVAILABLE`;
- **no notification**, by the same two checks as R1.

If location cannot be turned off, or the address reads fine anyway, this run is **INCONCLUSIVE**
rather than a FAIL. Say so and move on; R1 carries the removal on its own.

### R3 - the two hint texts, on screen

No connection needed. Settings, wireless section, **Basic** tab.

**R3a, hotspot.** `wifi-connection-mode=3`, `native-ap-transport=1`. The note under "Hotspot band"
must read, exactly:

> Auto asks for 5 GHz and falls back to 2.4 GHz. 2.4 GHz is reliable up to 720p, so pick 5 GHz only
> above that.

**R3b, WiFi Direct.** `native-ap-transport=0`. The note under "WiFi Direct band" must read, exactly:

> Auto asks for 5 GHz and falls back to 2.4 GHz, and on Android 9 and older asks for a 5 GHz channel
> first. 2.4 GHz is reliable up to 720p, so pick 5 GHz only above that.

**PASS:** both verbatim, both fully on screen without scrolling the note itself, and neither pushing
the next row off the screen. One screenshot each. A wording difference is a FAIL; report what is
actually there rather than paraphrasing.

### R4 - the WiFi Direct band selector is the first row under the transport selector

`wifi-connection-mode=3`, `native-ap-transport=0`. Check the **Advanced** tab, which is where this
change is visible.

**PASS:** reading down from "Android Auto mode", the order is the transport selector, then "WiFi
Direct band", then its note, then "Use the upper 5 GHz range". "Bluetooth adapter", "secondary
Bluetooth service" and the WiFi version exchange toggle all come **after** those, where they used to
come before. One screenshot showing the transport selector and the band row together.

Then switch to **Basic** and confirm "WiFi Direct band" is still there with its note. Then set the
band to **2.4 GHz only** and confirm "Use the upper 5 GHz range" disappears, as round 3's R7c
established; set it back to Auto and confirm it returns. Back out without saving, and confirm
`wifi-direct-band` is unchanged on disk.

## 6. Do not re-run

Everything else in round 3 is settled and this round must not spend time on it. Specifically not
R2 (the record surviving a publish), R3 (the dismissal sequence), R5 (the BSSID disproof), R7d
(Static BSSID visibility across five mode combinations), R7e (the remedy deep link) or R8 (the band
reaching the group, and the migration). Round 2's settled runs likewise.

## 7. Report back

Four numbers and two screenshots decide it:

1. **R0's test count.** 698 or a reason why not.
2. **R1's notification count.** Zero from this app beyond the ongoing one.
3. **Whether the banner still works.** R1 and R2 both. This is the regression check, and it matters
   more than the removal.
4. **Whether the two hint texts match verbatim.**

Round 3's own findings are already folded into `TESTING-TEMPLATE.md` §7a and need no further action.
