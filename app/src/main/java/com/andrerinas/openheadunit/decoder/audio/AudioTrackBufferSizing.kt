package com.andrerinas.openheadunit.decoder.audio

/** Sizing for the 48kHz stereo PCM16 AudioTrack, in its input frames (four bytes each).
 * getMinBufferSize is a platform estimate, not a HAL period or a guaranteed glitch-free size.
 * Use the whole estimate as a floor; the output controller can add scheduling reserve. */
internal object AudioTrackBufferSizing {
    /** The whole buffer below API 24, where a track cannot resize. 200 ms outlasts an 80 ms mixer stall. */
    const val FIXED_FRAMES = 48000 * 200 / 1000

    fun minimumFrames(minimumBytes: Int): Int {
        require(minimumBytes > 0)
        return maxOf(960, ((minimumBytes.toLong() + 3) / 4).toInt())
    }

    fun allocationBytes(minimumFrames: Int, canResize: Boolean): Int {
        require(minimumFrames in 1..Int.MAX_VALUE / 4)
        // Keep growth space even when the platform floor exceeds the usual 400ms capacity.
        // Older Android cannot resize a live track, so its one size must outlast a mixer stall.
        val frames = if (canResize) maxOf(19200,
            OutputBufferPolicy(48000, 480, minimumFrames).maximumFrames)
        else maxOf(minimumFrames, FIXED_FRAMES)
        val bytes = frames.toLong() * 4
        require(bytes <= Int.MAX_VALUE) { "AudioTrack buffer is too large: $frames frames" }
        return bytes.toInt()
    }
}
