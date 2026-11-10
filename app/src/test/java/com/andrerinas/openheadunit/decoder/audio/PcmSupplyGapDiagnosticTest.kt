package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PcmSupplyGapDiagnosticTest {
    private val packet = ShortArray(2048 * 2) { 1000 }

    private fun pacedBank(media: Boolean = true): Pair<AdaptivePcmBuffer, Long> {
        val bank = AdaptivePcmBuffer(latencyMultiplier = 16, isMediaSink = media)
        var now = 0L
        repeat(5) { bank.noteArrival(now, 2048); bank.write(packet, packet.size, now); now += 43 }
        return bank to now - 43
    }

    private fun AdaptivePcmBuffer.deliver(at: Long) {
        noteArrival(at, 2048)
        write(packet, packet.size, at)
    }

    @Test fun `a 700 ms gap reports its size and both reserves`() {
        val (bank, last) = pacedBank()
        bank.deliver(last + 700)
        val gap = bank.takeSupplyGap()!!
        assertEquals(700L, gap.gapMs)
        assertEquals(0, gap.more)
        assertFalse(gap.mediaHeld)
        assertEquals(0L, gap.stallBeforeMs)
        assertEquals(1000L, gap.stallAfterMs)
        assertNull(bank.takeSupplyGap())
    }

    @Test fun `a gap under 200 ms reports nothing`() {
        val (bank, last) = pacedBank()
        bank.deliver(last + 150)
        assertNull(bank.takeSupplyGap())
        val (guidance, glast) = pacedBank(media = false)
        guidance.deliver(glast + 700)
        assertNull(guidance.takeSupplyGap())
    }

    @Test fun `two gaps in one drain keep the larger`() {
        val (bank, last) = pacedBank()
        bank.deliver(last + 300)
        bank.deliver(last + 900)
        val gap = bank.takeSupplyGap()!!
        assertEquals(600L, gap.gapMs)
        assertEquals(1, gap.more)
    }
}
