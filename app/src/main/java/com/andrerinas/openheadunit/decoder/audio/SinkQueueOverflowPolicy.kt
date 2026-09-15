package com.andrerinas.openheadunit.decoder.audio

/**
 * How deep the hand-off queue in front of a sink may get, and what to shed when it fills.
 *
 * The setting counts chunks, but a chunk is about 20 ms on the 48 kHz media sink and several times
 * that on a 16 kHz prompt sink, so one number cannot mean the same on two channels. Stated in
 * milliseconds here and converted per channel.
 *
 * A full queue sheds its oldest chunk rather than refusing the new one: the stale end is the one
 * nobody will miss, and refusing the newest leaves the sink further behind for as long as the burst
 * lasts. [AudioTrackWrapper.write] is where that happens.
 */
object SinkQueueOverflowPolicy {

    /** Depth the queue must reach whatever the setting says. */
    const val MIN_QUEUE_MS = 1_000L

    /** The wireless unacked window AapControl advertises for audio; a legal burst must fit. */
    const val UNACKED_WINDOW_CHUNKS = 30

    /**
     * Chunks to allow. [configuredChunks] of zero means the user asked for no limit and gets none.
     */
    fun capacityChunks(configuredChunks: Int, chunkDurationMs: Int): Int {
        if (configuredChunks <= 0) return 0
        val byTime = if (chunkDurationMs > 0) {
            ((MIN_QUEUE_MS + chunkDurationMs - 1) / chunkDurationMs).toInt()
        } else {
            0
        }
        return maxOf(configuredChunks, byTime, UNACKED_WINDOW_CHUNKS)
    }

    fun chunkDurationMs(chunkFrames: Int, sampleRateInHz: Int): Int =
        if (sampleRateInHz <= 0 || chunkFrames <= 0) 0
        else (chunkFrames.toLong() * 1000L / sampleRateInHz).toInt()
}
