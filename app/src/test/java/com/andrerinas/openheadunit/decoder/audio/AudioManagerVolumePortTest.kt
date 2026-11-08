package com.andrerinas.openheadunit.decoder.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AudioManagerVolumePortTest {

    private val audio = mock<AudioManager>()
    private val lines = mutableListOf<String>()
    private val port = AudioManagerVolumePort(audio) { lines += it }

    @Test
    fun `a refused adjust is logged and does not throw`() {
        doThrow(SecurityException("dnd")).whenever(audio).adjustStreamVolume(3, AudioManager.ADJUST_LOWER, 0)
        port.adjust(3, raise = false)
        assertEquals(1, lines.size)
        assertTrue(lines[0].startsWith("AudioManagerVolumePort: adjust refused on stream 3"))
    }

    @Test
    fun `adjust passes the direction and no flags`() {
        port.adjust(3, raise = true)
        verify(audio).adjustStreamVolume(3, AudioManager.ADJUST_RAISE, 0)
        port.adjust(3, raise = false)
        verify(audio).adjustStreamVolume(3, AudioManager.ADJUST_LOWER, 0)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `index reads the stream volume`() {
        whenever(audio.getStreamVolume(3)).thenReturn(6)
        assertEquals(6, port.index(3))
    }
}
