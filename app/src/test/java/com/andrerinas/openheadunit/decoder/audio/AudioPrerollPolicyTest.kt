package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two ways the wait must end early: a stream too short to reach the target, and a chunk
 * whose write would deadlock the thread that starts playback. How deep the target is belongs to
 * [AudioJitterBufferPolicy] and is tested there.
 */
class AudioPrerollPolicyTest {

    @Test
    fun `an empty track does not start`() {
        // The whole point: this is the state that underran on every media start.
        assertFalse(AudioPrerollPolicy.shouldStart(0, 0, 9_600, 0))
    }

    @Test
    fun `a partly filled track does not start before its target`() {
        assertFalse(AudioPrerollPolicy.shouldStart(9_000, 0, 9_600, 10))
    }

    @Test
    fun `reaching the target starts playback`() {
        assertTrue(AudioPrerollPolicy.shouldStart(9_600, 0, 9_600, 10))
    }

    @Test
    fun `the pending chunk counts toward the target`() {
        // Decided before the write, so the frames about to land are part of the decision.
        assertFalse(AudioPrerollPolicy.shouldStart(9_000, 0, 9_600, 10))
        assertTrue(AudioPrerollPolicy.shouldStart(9_000, 600, 9_600, 10))
    }

    @Test
    fun `a stream too short for the target starts on the deadline`() {
        // A notification blip can be the whole message; waiting for an unreachable target would
        // silence it.
        assertFalse(AudioPrerollPolicy.shouldStart(500, 0, 9_600, AudioPrerollPolicy.MAX_WAIT_MS - 1))
        assertTrue(AudioPrerollPolicy.shouldStart(500, 0, 9_600, AudioPrerollPolicy.MAX_WAIT_MS))
    }

    @Test
    fun `the deadline never starts a track nothing has been written to`() {
        // A stream that has not begun, not a short one. Starting here reintroduces the bug.
        assertFalse(AudioPrerollPolicy.shouldStart(0, 0, 9_600, AudioPrerollPolicy.MAX_WAIT_MS * 10))
    }

    @Test
    fun `an ordinary stream reaches its target by fill and not by deadline`() {
        // Audio arrives at real time, so the target takes its own worth of wall clock. A deadline
        // inside that would decide every start and the banked depth would be arbitrary.
        assertTrue(AudioPrerollPolicy.MAX_WAIT_MS > AudioJitterBufferPolicy.TARGET_MS)
    }
}

/** The channel-conditioned deadline, added after a starved link started a music sink at 42 ms. */
class AudioPrerollPolicyDeadlineTest {

    @Test
    fun `a music sink waits longer than a prompt before starting short`() {
        assertTrue(
            AudioPrerollPolicy.maxWaitMs(isMediaSink = true) >
                AudioPrerollPolicy.maxWaitMs(isMediaSink = false)
        )
    }

    @Test
    fun `the prompt deadline is unchanged, so a blip still plays`() {
        assertEquals(AudioPrerollPolicy.MAX_WAIT_MS, AudioPrerollPolicy.maxWaitMs(false))
        assertTrue(AudioPrerollPolicy.shouldStart(1, 0, 9600, AudioPrerollPolicy.MAX_WAIT_MS))
    }

    @Test
    fun `a music sink does not start at 2048 frames where a prompt would`() {
        val elapsed = AudioPrerollPolicy.MAX_WAIT_MS + 58 // #979's measured 358ms
        val mediaWait = AudioPrerollPolicy.maxWaitMs(true)
        assertTrue(AudioPrerollPolicy.shouldStart(2048, 0, 9600, elapsed, AudioPrerollPolicy.MAX_WAIT_MS))
        assertFalse(AudioPrerollPolicy.shouldStart(2048, 0, 9600, elapsed, mediaWait))
    }

    @Test
    fun `a music sink still starts rather than staying silent forever`() {
        val mediaWait = AudioPrerollPolicy.maxWaitMs(true)
        assertTrue(AudioPrerollPolicy.shouldStart(2048, 0, 9600, mediaWait, mediaWait))
    }

    @Test
    fun `reaching the target still wins over either deadline`() {
        assertTrue(AudioPrerollPolicy.shouldStart(9600, 0, 9600, 0, AudioPrerollPolicy.maxWaitMs(true)))
    }
}
