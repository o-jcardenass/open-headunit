package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSinkSetupPolicyTest {

    @Test
    fun `no sink yet means build one`() {
        assertTrue(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = false, builtAsAac = null, setupAac = true))
        assertTrue(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = false, builtAsAac = false, setupAac = false))
    }

    @Test
    fun `a repeated setup on the same codec keeps the sink`() {
        assertFalse(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = true, builtAsAac = true, setupAac = true))
        assertFalse(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = true, builtAsAac = false, setupAac = false))
    }

    @Test
    fun `a setup that changes the codec rebuilds`() {
        assertTrue(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = true, builtAsAac = false, setupAac = true))
        assertTrue(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = true, builtAsAac = true, setupAac = false))
    }

    @Test
    fun `a setup naming no audio codec leaves a live sink alone`() {
        assertFalse(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = true, builtAsAac = true, setupAac = null))
    }

    @Test
    fun `a track we cannot read the codec of is rebuilt`() {
        assertTrue(AudioSinkSetupPolicy.rebuilds(hasLiveTrack = true, builtAsAac = null, setupAac = true))
    }
}
