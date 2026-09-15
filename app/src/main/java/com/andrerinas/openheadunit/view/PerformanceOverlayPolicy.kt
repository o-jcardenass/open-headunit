package com.andrerinas.openheadunit.view

/**
 * The decidable half of the projection performance overlay: the two CPU figures it prints beside
 * each other, and the temperature it picks out of the thermal zones.
 */
object PerformanceOverlayPolicy {

    /**
     * Process CPU time sums every thread, so on a multi-core unit it can exceed the wall clock it
     * is measured over. Divided by the core count it reads on the same 0-100 scale as the system
     * figure printed next to it, and the clamp covers a unit that hotplugs cores offline.
     */
    fun appCpuPercent(cpuDeltaMs: Long, elapsedDeltaMs: Long, coreCount: Int): Int {
        val elapsed = elapsedDeltaMs.coerceAtLeast(1L)
        val cpu = cpuDeltaMs.coerceAtLeast(0L)
        val cores = coreCount.coerceAtLeast(1)
        return ((cpu.toDouble() / (elapsed * cores)) * 100.0).toInt().coerceIn(0, 100)
    }

    /** Busy share of /proc/stat's aggregate cpu line, which already sums every core. */
    fun totalCpuPercent(totalDelta: Long, idleDelta: Long): Int {
        val total = totalDelta.coerceAtLeast(1L)
        val idle = idleDelta.coerceAtLeast(0L)
        return (((total - idle).toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
    }

    /**
     * The hottest zone that answered. A zone that could not be read is a null entry and is skipped:
     * one EINVAL zone used to discard the whole scan, including the seventeen that read fine.
     */
    fun temperatureC(rawZoneValues: List<Int?>): Int? = rawZoneValues
        .mapNotNull { raw ->
            when {
                raw == null -> null
                raw in 10000..125000 -> raw / 1000
                raw in 10..125 -> raw
                else -> null
            }
        }
        .maxOrNull()
}
