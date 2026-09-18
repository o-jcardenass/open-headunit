package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaxUnackedPolicyTest {

    private fun video(wireless: Boolean, softwareHevc: Boolean) =
        MaxUnackedPolicy.forChannel(Channel.ID_VID, wireless, softwareHevc)

    @Test
    fun `hardware decode keeps the wide video window a keyframe needs`() {
        assertEquals(12, video(wireless = true, softwareHevc = false))
        assertEquals(16, video(wireless = false, softwareHevc = false))
    }

    @Test
    fun `bundled software HEVC is held closer to decoder pace`() {
        assertEquals(6, video(wireless = true, softwareHevc = true))
        assertEquals(8, video(wireless = false, softwareHevc = true))
    }

    @Test
    fun `software HEVC is always narrower than hardware on the same link`() {
        for (wireless in listOf(true, false)) {
            assertTrue(video(wireless, softwareHevc = true) < video(wireless, softwareHevc = false))
        }
    }

    @Test
    fun `audio takes a wider jitter window on wireless and the codec never enters into it`() {
        for (channel in listOf(Channel.ID_AUD, Channel.ID_AU1, Channel.ID_AU2, Channel.ID_MIC)) {
            assertEquals(30, MaxUnackedPolicy.forChannel(channel, wireless = true, bundledSoftwareHevc = false))
            assertEquals(30, MaxUnackedPolicy.forChannel(channel, wireless = true, bundledSoftwareHevc = true))
            assertEquals(16, MaxUnackedPolicy.forChannel(channel, wireless = false, bundledSoftwareHevc = false))
        }
    }
}
