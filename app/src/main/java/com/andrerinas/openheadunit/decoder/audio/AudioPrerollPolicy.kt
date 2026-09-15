package com.andrerinas.openheadunit.decoder.audio

/**
 * When to start playback on an [android.media.AudioTrack], given how much is banked.
 *
 * The track was started the moment it was built, on an empty buffer, and all five media-stream
 * starts in a measured capture underran within a second. Audio arrives at real time, so the writer
 * never fills a buffer faster than the mixer drains it and the opening deficit is never recovered -
 * which is also why a deep [com.andrerinas.openheadunit.utils.Settings.audioLatencyMultiplier]
 * bought so little.
 *
 * Bank frames first and play once there are enough, as [AudioMixer] already does for its channels.
 * How deep to bank is [AudioJitterBufferPolicy]'s; this object only decides when the wait is over.
 *
 * Pure and clock-free: the caller passes the elapsed time in.
 */
object AudioPrerollPolicy {

    /**
     * Start anyway once this long has passed with anything at all banked.
     *
     * A notification blip can be shorter than the target, and waiting for one it will never reach
     * would silence it. Set beyond the default target so an ordinary stream still starts on fill.
     */
    const val MAX_WAIT_MS = 300L

    /**
     * The same escape for a music sink, which is not a blip and has nothing to gain from starting
     * nearly empty. Measured: a starved link started one at 2048 frames against a 9600 target and,
     * with nothing to re-bank it, it underran for the rest of the session.
     */
    const val MEDIA_MAX_WAIT_MS = 1_500L

    fun maxWaitMs(isMediaSink: Boolean): Long = if (isMediaSink) MEDIA_MAX_WAIT_MS else MAX_WAIT_MS

    /**
     * Whether the track should be started now.
     *
     * @param framesBanked frames already written to the track.
     * @param framesIncoming frames about to be written, counted here so the decision is made before
     *   the write rather than after it. See [AudioJitterBufferPolicy.MAX_FILL_NUMERATOR].
     * @param targetFrames from [AudioJitterBufferPolicy.targetFrames].
     * @param elapsedMs since the track was built.
     */
    fun shouldStart(
        framesBanked: Long,
        framesIncoming: Int,
        targetFrames: Int,
        elapsedMs: Long,
        maxWaitMs: Long = MAX_WAIT_MS
    ): Boolean {
        val total = framesBanked + framesIncoming
        if (total >= targetFrames) return true
        // Nothing banked is a stream that has not begun, not a short one. Starting on it would be
        // the empty-buffer start again, with a timer in front of it.
        return total > 0 && elapsedMs >= maxWaitMs
    }
}
