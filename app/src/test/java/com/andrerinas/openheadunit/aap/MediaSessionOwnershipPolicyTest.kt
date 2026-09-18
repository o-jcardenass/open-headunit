package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionOwnershipPolicyTest {

    @Test
    fun `a session to a separate phone is ours to hold`() {
        assertTrue(MediaSessionOwnershipPolicy.ownsMediaSession(isLoopbackSession = false))
    }

    @Test
    fun `self mode leaves the media session to the player on this device`() {
        // Self Mode announces no media sink, so the audio never reaches us and an active session
        // of ours would only outrank the real one.
        assertFalse(MediaSessionOwnershipPolicy.ownsMediaSession(isLoopbackSession = true))
    }
}
