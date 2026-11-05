package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioRouteReportTest {
    @Test fun `a null first value prints nothing`() {
        assertNull(AudioRouteReport().changed(null))
    }

    @Test fun `the first name prints and a repeat does not`() {
        val r = AudioRouteReport()
        assertEquals("speaker type=2", r.changed("speaker type=2"))
        assertNull(r.changed("speaker type=2"))
    }

    @Test fun `a change prints and a return to null prints nothing`() {
        val r = AudioRouteReport()
        r.changed("speaker type=2")
        assertEquals("usb type=11", r.changed("usb type=11"))
        assertNull(r.changed(null))
    }
}
