package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioJitterBufferPolicyTest {

    /** An AAP audio message payload. The media channel's, and the largest of the three. */
    private val AAP_AUDIO_MESSAGE_BYTES = 8192

    // The media sink as the phone actually drives it: 48 kHz stereo 16-bit, 8192-byte messages.
    private val mediaChunkFrames = AudioJitterBufferPolicy.arrivalChunkFrames(
        AAP_AUDIO_MESSAGE_BYTES, bytesPerFrame = 4
    )

    // AudioMixer drains one 20 ms cycle at a time: 48000 * 0.020 = 960 frames.
    private val mixerDrainFrames = 960

    @Test
    fun `an 8192 byte message is 2048 frames of 48 kHz stereo`() {
        assertEquals(2048, mediaChunkFrames)
    }

    @Test
    fun `the mixer's old three-cycle pre-roll could not hold, which is the defect`() {
        // 3 * 960 = 2880 frames banked, 2048 arriving at a time, 960 drained per cycle.
        // The trough is 832 frames against a 960-frame drain, so it re-banks forever.
        assertFalse(AudioJitterBufferPolicy.holdsAgainst(2880, mediaChunkFrames, mixerDrainFrames))
    }

    @Test
    fun `the computed target holds against the same arrival`() {
        val target = AudioJitterBufferPolicy.targetFrames(
            sampleRateInHz = 48000,
            arrivalChunkFrames = mediaChunkFrames,
            drainQuantumFrames = mixerDrainFrames,
            capacityFrames = 0
        )
        assertTrue(AudioJitterBufferPolicy.holdsAgainst(target, mediaChunkFrames, mixerDrainFrames))
    }

    @Test
    fun `it still holds when the buffer cap is the binding constraint`() {
        // The mixer's own output buffer today: 30720 bytes = 7680 frames.
        val target = AudioJitterBufferPolicy.targetFrames(48000, mediaChunkFrames, mixerDrainFrames, 7680)
        assertEquals(5760, target) // 3/4 of 7680
        assertTrue(AudioJitterBufferPolicy.holdsAgainst(target, mediaChunkFrames, mixerDrainFrames))
    }

    @Test
    fun `a large arrival chunk raises the target above the time-based figure`() {
        // 200 ms at 48 kHz is 9600 frames; a 12288-frame chunk needs more than that.
        val target = AudioJitterBufferPolicy.targetFrames(48000, 12288, 960, 0)
        assertTrue(target > 9600)
        assertTrue(AudioJitterBufferPolicy.holdsAgainst(target, 12288, 960))
    }

    @Test
    fun `a prompt channel is not delayed past the ceiling for no reason`() {
        // A 20 ms chunk cannot justify banking more than the time-based target.
        val target = AudioJitterBufferPolicy.targetFrames(48000, 960, 960, 0)
        assertEquals(9600, target)
        assertTrue(target <= AudioJitterBufferPolicy.framesFor(48000, AudioJitterBufferPolicy.MAX_TARGET_MS))
    }

    @Test
    fun `the ceiling never breaks the invariant for an oversized chunk`() {
        // 30720 output frames is 640 ms of chunk, well past the 400 ms ceiling. The invariant wins.
        val chunk = 30720
        val target = AudioJitterBufferPolicy.targetFrames(48000, chunk, 960, 0)
        assertTrue(target > AudioJitterBufferPolicy.framesFor(48000, AudioJitterBufferPolicy.MAX_TARGET_MS))
        assertTrue(AudioJitterBufferPolicy.holdsAgainst(target, chunk, 960))
    }

    @Test
    fun `the 16 kHz mono prompt channel holds against its own arrival`() {
        // 16 kHz mono: 2 bytes per frame, so an 8192-byte message is 4096 frames = 256 ms.
        val chunk = AudioJitterBufferPolicy.arrivalChunkFrames(8192, bytesPerFrame = 2)
        assertEquals(4096, chunk)
        val target = AudioJitterBufferPolicy.targetFrames(16000, chunk, 320, 0)
        assertTrue(AudioJitterBufferPolicy.holdsAgainst(target, chunk, 320))
    }

    @Test
    fun `a nonsense sample rate still yields a playable target`() {
        assertEquals(1, AudioJitterBufferPolicy.targetFrames(0, 2048, 960, 0))
    }

    @Test
    fun `a buffer too small to hold any workable target is reported as such`() {
        // 2048 frames of capacity: 3/4 is 1536, which cannot cover a 2048-frame arrival at all.
        assertFalse(AudioJitterBufferPolicy.capacityIsSufficient(2048, mediaChunkFrames, mixerDrainFrames))
        // The 640 ms buffer #979's unit granted is ample.
        assertTrue(AudioJitterBufferPolicy.capacityIsSufficient(30752, mediaChunkFrames, mixerDrainFrames))
    }

    @Test
    fun `zero capacity is insufficient rather than unlimited`() {
        assertFalse(AudioJitterBufferPolicy.capacityIsSufficient(0, mediaChunkFrames, mixerDrainFrames))
    }
}

/**
 * The latency setting, which used to size the track's capacity and nothing a listener could hear.
 * Round 1 measured the cushion at a flat 200 ms at every setting, on both audio paths.
 */
class AudioJitterBufferDialTest {

    private val rate = 48_000

    // 8192 bytes of 48 kHz stereo: 2048 frames, 42.7 ms.
    private val mediaChunkFrames = 2_048
    private val mixerDrainFrames = 960

    @Test
    fun `the default setting leaves the cushion where it was`() {
        assertEquals(
            AudioJitterBufferPolicy.TARGET_MS,
            AudioJitterBufferPolicy.targetMsFor(AudioJitterBufferPolicy.ANCHOR_MULTIPLIER)
        )
    }

    @Test
    fun `the dial buys depth above the default and gives it back below`() {
        val deep = AudioJitterBufferPolicy.targetMsFor(16)
        val shallow = AudioJitterBufferPolicy.targetMsFor(2)
        assertTrue(deep > AudioJitterBufferPolicy.TARGET_MS)
        assertTrue(shallow < AudioJitterBufferPolicy.TARGET_MS)
    }

    @Test
    fun `the deep end stops where the audio would fall behind the picture`() {
        assertEquals(AudioJitterBufferPolicy.MAX_TARGET_MS, AudioJitterBufferPolicy.targetMsFor(64))
    }

    @Test
    fun `a nonsense setting still asks for something playable`() {
        assertTrue(AudioJitterBufferPolicy.targetMsFor(0) >= AudioJitterBufferPolicy.MIN_TARGET_MS)
        assertTrue(AudioJitterBufferPolicy.targetMsFor(-4) >= AudioJitterBufferPolicy.MIN_TARGET_MS)
    }

    @Test
    fun `no setting can produce a target that oscillates`() {
        // The whole reason the dial is floored rather than free: a target under one arrival plus
        // one drain re-banks forever, which is the defect this thread started on.
        for (multiplier in intArrayOf(1, 2, 4, 8, 16)) {
            val target = AudioJitterBufferPolicy.targetFrames(
                sampleRateInHz = rate,
                arrivalChunkFrames = mediaChunkFrames,
                drainQuantumFrames = mixerDrainFrames,
                capacityFrames = 30_752,
                latencyMultiplier = multiplier
            )
            assertTrue(
                "multiplier $multiplier gave a target of $target that cannot hold",
                AudioJitterBufferPolicy.holdsAgainst(target, mediaChunkFrames, mixerDrainFrames)
            )
        }
    }

    @Test
    fun `the dial moves the target it is allowed to move`() {
        fun targetAt(multiplier: Int) = AudioJitterBufferPolicy.targetFrames(
            sampleRateInHz = rate,
            arrivalChunkFrames = mediaChunkFrames,
            drainQuantumFrames = 1,
            capacityFrames = 61_504,
            latencyMultiplier = multiplier
        )
        assertTrue(targetAt(16) > targetAt(8))
        assertTrue(targetAt(8) > targetAt(1))
    }
}

/** The re-bank, which round 1 measured re-underrunning because it banked the opening depth. */
class AudioJitterBufferRebankTest {

    private val rate = 48_000

    @Test
    fun `a re-bank is deeper than the opening bank`() {
        val target = 9_600
        val rebank = AudioJitterBufferPolicy.rebankTargetFrames(target, rate, 30_752)
        assertTrue(rebank > target)
    }

    @Test
    fun `a re-bank clears the disturbance that caused it`() {
        // Measured: video dispatch held the read thread for 203 to 233 ms, and a 200 ms re-bank
        // underran again inside the same window.
        val target = AudioJitterBufferPolicy.framesFor(rate, AudioJitterBufferPolicy.TARGET_MS)
        val rebank = AudioJitterBufferPolicy.rebankTargetFrames(target, rate, 30_752)
        assertTrue(rebank > AudioJitterBufferPolicy.framesFor(rate, 233L))
    }

    @Test
    fun `a re-bank still leaves the write that ends it somewhere to land`() {
        // Each target is one the capacity could really produce, since targetFrames caps by the
        // same buffer share. A write onto a track banked to capacity blocks on the thread that
        // would start it.
        for (capacity in intArrayOf(19_200, 30_752, 61_504)) {
            val target = AudioJitterBufferPolicy.targetFrames(rate, 2_048, 1, capacity)
            val rebank = AudioJitterBufferPolicy.rebankTargetFrames(target, rate, capacity)
            assertTrue("re-bank $rebank must stay under capacity $capacity", rebank < capacity)
            assertTrue(rebank >= target)
        }
    }

    @Test
    fun `an unreadable capacity does not collapse the re-bank`() {
        assertTrue(AudioJitterBufferPolicy.rebankTargetFrames(9_600, rate, 0) > 9_600)
        assertEquals(9_600, AudioJitterBufferPolicy.rebankTargetFrames(9_600, 0, 30_752))
    }
}
