package com.andrerinas.openheadunit.aap

/**
 * Compare PCM source and delivery intervals without assuming synchronized device clocks.
 * These are diagnostic observations, not one-way latency or proof of packet loss. Capture
 * scheduling and phone-side pruning can both move source timestamps. Never drives playback.
 */
internal class AudioTimestampMonitor(private val windowUs: Long = 10_000_000L) {
    private var windowStartUs = -1L
    private var previousSourceUs = -1L
    private var previousArrivalUs = -1L
    private var previousDurationUs = 0L
    private var packets = 0
    private var comparablePairs = 0
    private var durationChanges = 0
    private var discontinuities = 0
    private var missingTimestamps = 0
    private var maxSourceGapUs = 0L
    private var maxArrivalGapUs = 0L
    private var maxSourceExcessUs = 0L
    private var maxArrivalExcessUs = 0L
    private var maxDeliveryIncreaseUs = 0L
    private var spikeArrivalUs = -1L
    private var spikeArrivalGapUs = 0L
    private var spikeSourceGapUs = -1L
    private var spikePreviousDurationUs = 0L
    private var spikeDurationUs = 0L
    /** The newest valid source interval in milliseconds, or -1 when the last pair had none. */
    @Volatile var lastSourceGapMs = -1L
        private set

    @Synchronized
    fun onPacket(sourceUs: Long, arrivalUs: Long, durationUs: Long): Report? {
        if (arrivalUs < 0 || durationUs <= 0) return null
        if (previousArrivalUs > arrivalUs) reset()
        if (windowStartUs < 0) windowStartUs = arrivalUs
        packets++
        lastSourceGapMs = -1L
        if (sourceUs <= 0) missingTimestamps++
        if (previousArrivalUs >= 0) {
            val arrivalGap = arrivalUs - previousArrivalUs
            val sourceGap = sourceUs - previousSourceUs
            // Start/Stop reset this monitor. Also rebase unannounced pauses/discontinuities.
            if (arrivalGap > MAX_INTERVAL_US) {
                discontinuities++
            } else {
                maxArrivalGapUs = maxOf(maxArrivalGapUs, arrivalGap)
                // Older senders stamp capture completion; newer ones subtract the captured
                // block's duration. A size change therefore has no single expected PTS delta.
                // Use a conservative arrival budget and exclude the ambiguous PTS comparison.
                val sameDuration = durationUs == previousDurationUs
                if (!sameDuration) durationChanges++
                val arrivalExcess = arrivalGap - maxOf(previousDurationUs, durationUs)
                if (arrivalExcess > maxArrivalExcessUs) {
                    maxArrivalExcessUs = arrivalExcess
                    // Keep the source interval from this exact pair, not the independent
                    // window maximum. Primitive fields avoid per-packet diagnostic allocation.
                    spikeArrivalUs = arrivalUs
                    spikeArrivalGapUs = arrivalGap
                    spikeSourceGapUs = if (sourceUs > 0 && previousSourceUs > 0 &&
                        sourceGap in 1..MAX_INTERVAL_US) sourceGap else -1L
                    spikePreviousDurationUs = previousDurationUs
                    spikeDurationUs = durationUs
                }
                if (sourceUs > 0 && previousSourceUs > 0) {
                    if (sourceGap in 1..MAX_INTERVAL_US) {
                        lastSourceGapMs = sourceGap / 1000
                        maxSourceGapUs = maxOf(maxSourceGapUs, sourceGap)
                        if (sameDuration) {
                            comparablePairs++
                            maxSourceExcessUs = maxOf(maxSourceExcessUs, sourceGap - previousDurationUs)
                            maxDeliveryIncreaseUs = maxOf(maxDeliveryIncreaseUs, arrivalGap - sourceGap)
                        }
                    } else {
                        discontinuities++
                    }
                }
            }
        }
        previousSourceUs = sourceUs
        previousArrivalUs = arrivalUs
        previousDurationUs = durationUs
        if (arrivalUs - windowStartUs < windowUs) return null
        val report = Report(packets, comparablePairs, durationChanges, missingTimestamps, discontinuities,
            maxSourceGapUs, maxArrivalGapUs, maxSourceExcessUs, maxArrivalExcessUs, maxDeliveryIncreaseUs,
            if (maxArrivalExcessUs >= 20_000L) ArrivalSpike(spikeArrivalUs, spikeArrivalGapUs,
                spikeSourceGapUs.takeIf { it >= 0 }, spikePreviousDurationUs, spikeDurationUs) else null)
        clearWindow()
        windowStartUs = arrivalUs
        return report
    }

    @Synchronized
    fun reset() {
        windowStartUs = -1L
        previousSourceUs = -1L
        previousArrivalUs = -1L
        previousDurationUs = 0L
        lastSourceGapMs = -1L
        clearWindow()
    }

    private fun clearWindow() {
        packets = 0
        comparablePairs = 0
        durationChanges = 0
        discontinuities = 0
        missingTimestamps = 0
        maxSourceGapUs = 0
        maxArrivalGapUs = 0
        maxSourceExcessUs = 0
        maxArrivalExcessUs = 0
        maxDeliveryIncreaseUs = 0
        spikeArrivalUs = -1L
        spikeArrivalGapUs = 0
        spikeSourceGapUs = -1L
        spikePreviousDurationUs = 0
        spikeDurationUs = 0
    }

    /** One receiver-clock event, suitable for correlation with deferred transport logs.
     * A valid source interval is still not proof of lost samples or one-way latency. */
    data class ArrivalSpike(
        val arrivalUs: Long,
        val arrivalGapUs: Long,
        val sourceGapUs: Long?,
        val previousDurationUs: Long,
        val durationUs: Long
    ) {
        val comparable: Boolean get() = sourceGapUs != null && previousDurationUs == durationUs

        override fun toString(): String = "arrivalSpikeElapsedMs=${arrivalUs / 1000}, " +
            "pairedArrivalGapMs=${arrivalGapUs / 1000}, " +
            "pairedSourceGapMs=${sourceGapUs?.div(1000) ?: "unknown"}, " +
            "pairedPreviousDurationUs=$previousDurationUs, pairedDurationUs=$durationUs, " +
            "pairedComparable=$comparable"
    }

    data class Report(
        val packets: Int,
        val comparablePairs: Int,
        val durationChanges: Int,
        val missingTimestamps: Int,
        val discontinuities: Int,
        val maxSourceGapUs: Long,
        val maxArrivalGapUs: Long,
        val maxSourceExcessUs: Long,
        val maxArrivalExcessUs: Long,
        val maxDeliveryIncreaseUs: Long,
        val arrivalSpike: ArrivalSpike?
    ) {
        val hasGap: Boolean get() = maxArrivalExcessUs >= 20_000L || maxSourceExcessUs >= 20_000L

        override fun toString(): String = "media timing: packets=$packets, pairs=$comparablePairs, " +
            "durationChanges=$durationChanges, missingPts=$missingTimestamps, discontinuities=$discontinuities, " +
            "sourceGapMaxMs=${maxSourceGapUs / 1000}, arrivalGapMaxMs=${maxArrivalGapUs / 1000}, " +
            "sourceExcessMaxMs=${maxSourceExcessUs / 1000}, " +
            "arrivalExcessMaxMs=${maxArrivalExcessUs / 1000}, " +
            "deliveryIncreaseMaxMs=${maxDeliveryIncreaseUs / 1000}" +
            (arrivalSpike?.let { ", $it" } ?: "")
    }

    companion object { private const val MAX_INTERVAL_US = 5_000_000L }
}

/**
 * AAC units drained from one PCM input share its timestamp. This groups them, so the monitor
 * sees one packet per timestamp with the summed duration. Primitive fields: no allocation per unit.
 */
internal class AacTimingBatcher {
    var closedSourceUs = 0L
        private set
    var closedArrivalUs = 0L
        private set
    var closedDurationUs = 0L
        private set
    /** Gap to the previous group, as if that group were one unit; -1 when it is not known. */
    var sourceGapUs = -1L
        private set
    private var sourceUs = -1L
    private var arrivalUs = 0L
    private var durationUs = 0L

    /** True when this unit starts a new timestamp and the previous group is now in the closed* fields. */
    @Synchronized
    fun onUnit(unitSourceUs: Long, unitArrivalUs: Long, unitDurationUs: Long): Boolean {
        sourceGapUs = -1L
        if (sourceUs >= 0 && unitSourceUs == sourceUs) {
            durationUs += unitDurationUs
            return false
        }
        val closed = sourceUs > 0 && unitSourceUs > 0
        if (closed) {
            closedSourceUs = sourceUs
            closedArrivalUs = arrivalUs
            closedDurationUs = durationUs
            if (unitSourceUs > sourceUs) {
                sourceGapUs = unitDurationUs + maxOf(0L, unitSourceUs - sourceUs - durationUs)
            }
        }
        sourceUs = unitSourceUs
        arrivalUs = unitArrivalUs
        durationUs = unitDurationUs
        return closed
    }

    @Synchronized
    fun reset() {
        sourceUs = -1L; arrivalUs = 0L; durationUs = 0L; sourceGapUs = -1L
    }
}
