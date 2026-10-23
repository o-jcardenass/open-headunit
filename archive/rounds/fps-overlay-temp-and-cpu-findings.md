# FPS/state overlay: missing temp on D-HU, app CPU% past 100%

Not a hardware round. A source read of `aap/AapProjectionActivity.kt`'s performance overlay
(`setupFpsCounter`, `PerformanceSampler`) plus one read-only `adb shell` dump against D-HU
(MT50, session already up) to confirm a device-side hypothesis. No settings changed, nothing
installed, no round consumed.

## 1. Temp reads "--" on D-HU but not on D-POCO / D-MOTO

The overlay's temperature source is a hand-rolled scan of `/sys/class/thermal/thermal_zone*/temp`
in `PerformanceSampler.readTemperatureC()` (`AapProjectionActivity.kt:2460-2478`). It is not
`BatteryManager`, `PowerManager.getCurrentThermalStatus()`, or `HardwarePropertiesManager` — just a
raw sysfs read, no permission declared, wrapped in a single outer `try/catch (e: Exception) { null
}` with no `AppLog` call, so a failure surfaces only as `Temp: --` with nothing in logcat to explain
it.

D-HU's thermal zones, read live:

```
thermal_zone0  soc-thmzone       67210  (ok)
thermal_zone1  cputop0-tzone0    65940  (ok)
...15 more zones, all ok...
thermal_zone13 osctsen-thmzone   cat: Invalid argument   <- EINVAL
thermal_zone14 outtsen-thmzone   cat: Invalid argument   <- EINVAL
thermal_zone18 battery           65000  (ok)
```

17 of 19 zones return a good value (`soc-thmzone` at 67°C, well inside the sanity range the code
already checks for). Two — `osctsen-thmzone` and `outtsen-thmzone` — throw `EINVAL` on read. That
looks like a UNISOC/Spreadtrum kernel quirk specific to this SoC family: zones registered in the
thermal framework but not wired to return a value.

**Root cause is in the per-zone loop, not the sysfs listing.** `readTemperatureC()`'s `mapNotNull`
lambda calls `zone.resolve("temp").readText()` with no per-zone try/catch. When that throws for
`thermal_zone13`, the exception isn't contained to that one element — it propagates straight out of
`mapNotNull` and up into the function's *outer* catch-all, which discards the whole scan, including
the 17 zones that already read fine. One dead zone nulls all of them.

D-POCO and D-MOTO most likely just don't expose any zone that errors this way on their SoCs, so the
same code happens to work there by accident of hardware rather than by design — this wasn't verified
independently against those two devices, only reasoned from the code path (any EINVAL zone would
reproduce the same blank-out regardless of vendor).

## 2. App CPU% shows past 100%

`PerformanceSampler.sample()` (`AapProjectionActivity.kt:2402-2429`) computes two CPU numbers that
look like they should share a scale but don't:

- `totalCpu` (system-wide) is derived from `/proc/stat`'s aggregate `cpu` line, which already sums
  all cores, so `(totalDelta - idleDelta) / totalDelta` is inherently a 0-100 fraction of total
  system capacity — and the code clamps it explicitly (`.coerceIn(0, 100)`).
- `appCpu` (this process) is `Process.getElapsedCpuTime()` delta over wall-clock delta ×100, which
  is percent of **one core-equivalent**. `getElapsedCpuTime()` sums CPU time across every thread of
  the process, so on a multi-core device, with several of this app's threads (decoder, network, UI)
  genuinely busy inside the same wall-clock window, the CPU-time delta can exceed the wall-clock
  delta — e.g. two threads fully busy for a 1000ms window yield ~2000ms of CPU time, i.e. 200%. The
  code only had a lower-bound clamp (`.coerceAtLeast(0)`), no upper bound, and no normalization
  against core count — so it was never on the same scale as `totalCpu` next to it in the overlay.

Not a data bug — the raw number is real multi-core usage — just displayed without normalizing to
match its neighbor.

## 3. Suggested plan: pull the overlay out of `AapProjectionActivity.kt`, add left/right placement

Not scoped, not branched, nothing queued — flagging for future planning only.

`AapProjectionActivity.kt` is already ~2000 lines and is called out in `.claude/CLAUDE.md` as the
fullscreen projection surface / orchestrator-adjacent file. The entire performance overlay lives
inside it: `setupFpsCounter()` (2304-2333), the `performanceOverlayRunnable` polling loop
(~137-141), `requestPerformanceOverlayUpdate()` (2344-2367), `buildPerformanceOverlayText()`
(2369-2383), and the `PerformanceSampler` inner class (2385-2479) — all self-contained (only touches
`container`, the `fpsTextView` it creates, and `settings.showFpsCounter`), so it's a clean lift into
its own file, e.g. an `aap/PerformanceOverlay.kt` class the activity just instantiates and calls
`start()`/`stop()`/`attachTo(container)` on. Matches the repo's "pure policy objects" convention for
the parts that are pure (`PerformanceSampler`'s reads don't touch the view at all and could be
tested directly rather than only reachable through the activity).

While it's being pulled out, worth adding a way to move the overlay from the left edge (current,
fixed) to the right — useful on rigs/panels where the left side is occluded (this doc's own D-HU has
an OEM bar; see `ultrawide-touch-round7` findings, "MT50 OEM bar = 136px on the right", the mirror
case). Likely shape: a `Settings` enum (`OverlayPosition.LEFT` / `RIGHT`, maybe top too) plus a
`LayoutParams.gravity` flip in the new file, no protocol or hardware-detection involved. The
temp/cpu fixes above are logic-only (`PerformanceSampler`) and don't depend on this refactor; the
positioning feature does need the extraction done first to have a sane place to put the toggle
logic.

## Sources

- `app/src/main/java/com/andrerinas/openheadunit/aap/AapProjectionActivity.kt`
- Live `adb shell` thermal zone dump against D-HU (MT50, `27870808938846`), 2026-09-15.
