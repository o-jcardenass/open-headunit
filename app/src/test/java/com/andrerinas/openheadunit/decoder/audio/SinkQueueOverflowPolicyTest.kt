package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SinkQueueOverflowPolicyTest {

    @Test
    fun `an 8192 byte media chunk is about 42 ms`() {
        assertEquals(42, SinkQueueOverflowPolicy.chunkDurationMs(2048, 48000))
    }

    @Test
    fun `the default 50 chunks already clears both floors on the media sink`() {
        assertEquals(50, SinkQueueOverflowPolicy.capacityChunks(50, 42))
    }

    @Test
    fun `a small setting is raised to hold the unacked window`() {
        // A legal 30-message burst must fit or we drop sound the protocol told the phone to send.
        assertEquals(
            SinkQueueOverflowPolicy.UNACKED_WINDOW_CHUNKS,
            SinkQueueOverflowPolicy.capacityChunks(5, 42)
        )
    }

    @Test
    fun `a long prompt chunk needs fewer entries and the window floor governs`() {
        // 16 kHz mono, 8192-byte message = 4096 frames = 256 ms, so 4 chunks make a second.
        val chunkMs = SinkQueueOverflowPolicy.chunkDurationMs(4096, 16000)
        assertEquals(256, chunkMs)
        assertEquals(SinkQueueOverflowPolicy.UNACKED_WINDOW_CHUNKS, SinkQueueOverflowPolicy.capacityChunks(10, chunkMs))
    }

    @Test
    fun `a very short chunk needs more entries to make the same milliseconds`() {
        // 10 ms chunks need 100 entries to hold a second, above both other floors.
        assertEquals(100, SinkQueueOverflowPolicy.capacityChunks(50, 10))
    }

    @Test
    fun `no limit stays no limit`() {
        assertEquals(0, SinkQueueOverflowPolicy.capacityChunks(0, 42))
    }

    @Test
    fun `an unreadable chunk duration falls back to the other floors`() {
        assertEquals(50, SinkQueueOverflowPolicy.capacityChunks(50, 0))
        assertEquals(0, SinkQueueOverflowPolicy.chunkDurationMs(2048, 0))
    }

    @Test
    fun `the floor clears the burst a parked read thread delivers at once`() {
        // Measured: video dispatch parked the read thread for 200 ms at a time and the audio it
        // had not read arrived in one burst on release, which is what filled the queue.
        val chunkMs = SinkQueueOverflowPolicy.chunkDurationMs(2_048, 48_000)
        val capacity = SinkQueueOverflowPolicy.capacityChunks(20, chunkMs)
        assertTrue(capacity * chunkMs >= SinkQueueOverflowPolicy.MIN_QUEUE_MS)
    }
}
