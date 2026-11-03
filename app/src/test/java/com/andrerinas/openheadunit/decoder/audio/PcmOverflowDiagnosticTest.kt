package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.*
import org.junit.Test

class PcmOverflowDiagnosticTest {
    private val CAP = AdaptivePcmBuffer.capacityFramesFor(48000)

    @Test fun `overflow records loss time and consumption before the delayed report`() {
        val buffer = AdaptivePcmBuffer(latencyMultiplier = 2)
        val full = ShortArray(CAP * 2) { 12000 }
        buffer.write(full, full.size, 0)
        val out = ShortArray(960)
        buffer.render(out, 100)
        assertNull(buffer.takeOverflow())
        buffer.write(ShortArray(1920), 1920, 150)
        buffer.write(ShortArray(960), 960, 160)
        val loss = requireNotNull(buffer.takeOverflow())
        assertEquals(150L, loss.atMs)
        assertEquals(100L, loss.lastReadMs)
        assertEquals(CAP - 480, loss.depthBeforeFrames)
        assertEquals(960, loss.incomingFrames)
        assertEquals(480, loss.oldFramesDropped)
        assertEquals(0, loss.incomingFramesDropped)
        assertTrue(loss.started)
        assertBalance(loss)
        assertEquals(960L, buffer.droppedFrames)
        assertNull(buffer.takeOverflow())
    }

    @Test fun `oversized writes distinguish existing and incoming loss and retain bounded capacity`() {
        val buffer = AdaptivePcmBuffer(latencyMultiplier = 2)
        buffer.write(ShortArray(960), 960, 0)
        buffer.finish()
        val oversized = ShortArray(2 * (CAP + 1000))
        buffer.write(oversized, oversized.size, 10)
        val loss = requireNotNull(buffer.takeOverflow())
        assertEquals(CAP, loss.capacityFrames)
        assertEquals(480, loss.oldFramesDropped)
        assertEquals(1000, loss.incomingFramesDropped)
        assertEquals(-1L, loss.lastReadMs)
        assertFalse(loss.started)
        assertTrue(loss.ended)
        assertBalance(loss)
        assertEquals(1480L, buffer.droppedFrames)
        assertEquals(CAP, buffer.depthFrames())
    }

    @Test fun `frame accounting includes reset loss without counting oversized input twice`() {
        val buffer = AdaptivePcmBuffer(latencyMultiplier = 2)
        val oversized = ShortArray(2 * (CAP + 1000))
        buffer.write(oversized, oversized.size, 0)
        buffer.render(ShortArray(960), 100)
        buffer.reset()
        buffer.takeOverflow()
        buffer.write(oversized, oversized.size, 200)
        val loss = requireNotNull(buffer.takeOverflow())
        assertEquals(CAP + 1000L, loss.offeredBeforeFrames)
        assertEquals(480L, loss.consumedFrames)
        assertEquals(1000L, loss.overflowBeforeFrames)
        assertEquals(CAP - 480L, loss.resetDiscardedFrames)
        assertEquals(-1L, loss.lastReadMs)
        assertBalance(loss)
    }

    private fun assertBalance(loss: AdaptivePcmBuffer.Overflow) {
        assertEquals(loss.depthBeforeFrames.toLong(), loss.offeredBeforeFrames - loss.consumedFrames -
            loss.compressedFrames - loss.overflowBeforeFrames - loss.resetDiscardedFrames)
    }
}
