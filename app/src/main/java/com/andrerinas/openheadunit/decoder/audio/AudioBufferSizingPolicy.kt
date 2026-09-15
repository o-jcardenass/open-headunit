package com.andrerinas.openheadunit.decoder.audio

/**
 * How large an [android.media.AudioTrack] to ask for.
 *
 * Capacity is fixed at construction and can only be requested, never grown, so ask generously. A
 * capacity below the jitter target cannot hold at all, which is why the multiplier has a floor
 * under it.
 *
 * There used to be a second half here that started the effective size small and grew it on
 * underrun. It was never called by anything, and it was the wrong idea anyway: shrinking the
 * effective size trades robustness for output latency, which is what a network fed sink has least
 * use for. The depth lives in [AudioJitterBufferPolicy] instead.
 */
object AudioBufferSizingPolicy {

    /** Capacity floor, whatever the latency multiplier works out to. */
    const val MIN_CAPACITY_MS = 400L

    fun framesFor(sampleRateInHz: Int, ms: Long): Int =
        if (sampleRateInHz <= 0) 0 else (sampleRateInHz.toLong() * ms / 1000L).toInt()

    /**
     * Bytes to request. [minBufferBytes] is the device minimum; a non-positive one is handed back
     * untouched so the caller keeps the framework's own error.
     */
    fun requestedBytes(
        minBufferBytes: Int,
        multiplier: Int,
        sampleRateInHz: Int,
        bytesPerFrame: Int
    ): Int {
        if (minBufferBytes <= 0) return minBufferBytes
        val byMultiplier = minBufferBytes * multiplier.coerceAtLeast(1)
        val floorBytes = framesFor(sampleRateInHz, MIN_CAPACITY_MS) * bytesPerFrame.coerceAtLeast(1)
        return maxOf(byMultiplier, floorBytes, minBufferBytes)
    }
}
