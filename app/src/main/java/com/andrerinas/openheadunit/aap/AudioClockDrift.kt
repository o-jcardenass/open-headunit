package com.andrerinas.openheadunit.aap

/**
 * Estimates how fast the phone's capture clock runs against ours. The minimum of
 * arrival minus source in each window drops link jitter, and a line through those minima
 * gives the drift in ppm. Constant memory; diagnostic only, never drives playback.
 */
internal class AudioClockDrift(
    private val windowUs: Long = 10_000_000L,
    private val reportEveryUs: Long = 60_000_000L,
    private val minWindows: Int = 3
) {
    private var windowStartUs = -1L
    private var windowMinUs = Long.MAX_VALUE
    private var firstWindowUs = -1L
    private var baseOffsetUs = 0L
    private var lastArrivalUs = -1L
    private var nextReportUs = -1L
    private var windows = 0
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumXX = 0.0
    private var sumXY = 0.0

    @Synchronized
    fun onPacket(sourceUs: Long, arrivalUs: Long): Report? {
        if (sourceUs <= 0 || arrivalUs < 0) return null
        if (lastArrivalUs > arrivalUs) reset()
        lastArrivalUs = arrivalUs
        if (windowStartUs < 0) windowStartUs = arrivalUs
        windowMinUs = minOf(windowMinUs, arrivalUs - sourceUs)
        if (arrivalUs - windowStartUs < windowUs) return null
        closeWindow()
        windowStartUs = arrivalUs
        if (nextReportUs < 0) nextReportUs = arrivalUs + reportEveryUs
        if (arrivalUs < nextReportUs) return null
        nextReportUs = arrivalUs + reportEveryUs
        return estimate()
    }

    private fun closeWindow() {
        if (firstWindowUs < 0) { firstWindowUs = windowStartUs; baseOffsetUs = windowMinUs }
        val x = (windowStartUs - firstWindowUs) / 1e6
        val y = (windowMinUs - baseOffsetUs).toDouble()
        windows++
        sumX += x; sumY += y; sumXX += x * x; sumXY += x * y
        windowMinUs = Long.MAX_VALUE
    }

    /** Null until [minWindows] windows have closed. */
    @Synchronized
    fun estimate(): Report? {
        if (windows < minWindows) return null
        val denominator = windows * sumXX - sumX * sumX
        if (denominator <= 0.0) return null
        // y is in microseconds and x in seconds, so the slope is microseconds per second: ppm.
        val ppm = (windows * sumXY - sumX * sumY) / denominator
        return Report(ppm, ((windows * windowUs) / 1_000_000L), windows)
    }

    @Synchronized
    fun reset() {
        windowStartUs = -1L
        windowMinUs = Long.MAX_VALUE
        firstWindowUs = -1L
        baseOffsetUs = 0L
        lastArrivalUs = -1L
        nextReportUs = -1L
        windows = 0
        sumX = 0.0; sumY = 0.0; sumXX = 0.0; sumXY = 0.0
    }

    data class Report(val ppm: Double, val spanSeconds: Long, val windows: Int) {
        override fun toString(): String =
            "clock drift=${if (ppm >= 0) "+" else ""}${Math.round(ppm)} ppm over ${spanSeconds}s ($windows windows)"
    }
}
