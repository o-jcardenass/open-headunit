package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.*
import org.junit.Test

class AudioTrackBufferSizingTest {
    @Test fun `platform byte estimates become whole stereo frames without losing the safety floor`() {
        assertEquals(960, AudioTrackBufferSizing.minimumFrames(768))
        assertEquals(960, AudioTrackBufferSizing.minimumFrames(3840))
        assertEquals(1922, AudioTrackBufferSizing.minimumFrames(7688))
        assertEquals(5928, AudioTrackBufferSizing.minimumFrames(23712))
        assertEquals(5929, AudioTrackBufferSizing.minimumFrames(23713))
    }

    @Test fun `large platform floors still have room for the output controller to grow`() {
        for (minimum in listOf(960, 3343, 5928, 19001, 19201, 30000)) {
            val capacity = AudioTrackBufferSizing.allocationBytes(minimum, true) / 4
            val policy = OutputBufferPolicy(48000, 480, minimum)
            assertTrue(capacity >= policy.maximumFrames)
            assertTrue(capacity >= 19200)
        }
    }

    @Test fun `below API 24 the one size is the fixed stall reserve or the platform floor`() {
        assertEquals(9600 * 4, AudioTrackBufferSizing.allocationBytes(960, false))
        assertEquals(9600 * 4, AudioTrackBufferSizing.allocationBytes(2229, false))
        assertEquals(19001 * 4, AudioTrackBufferSizing.allocationBytes(19001, false))
    }

    @Test fun `a fixed track rides out mixer stalls that underrun the platform floor`() {
        // The device drains 48 frames a ms; the mixer tops it up to capacity except during a stall.
        fun xruns(capacity: Int, stallMs: Int): Int {
            var buffered = capacity
            var xruns = 0
            for (now in 1L..120_000L) {
                val stalled = now >= 5000 && now % 5000 < stallMs
                if (!stalled) buffered = capacity
                if (buffered in 1..48) xruns++
                buffered = (buffered - 48).coerceAtLeast(0)
            }
            return xruns
        }
        val fixed = AudioTrackBufferSizing.allocationBytes(2229, false) / 4
        for (stallMs in intArrayOf(30, 80)) assertEquals("stall=${stallMs}ms", 0, xruns(fixed, stallMs))
        assertTrue(xruns(2229, 80) > 0)
    }

    @Test fun `invalid or unrepresentable platform sizes fail before creating a track`() {
        for (bytes in listOf(-2, -1, 0)) {
            assertThrows(IllegalArgumentException::class.java) { AudioTrackBufferSizing.minimumFrames(bytes) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AudioTrackBufferSizing.allocationBytes(AudioTrackBufferSizing.minimumFrames(Int.MAX_VALUE), true)
        }
    }
}
