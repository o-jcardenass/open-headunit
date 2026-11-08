package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaReservePolicyTest {
    private fun ms(v: Int) = v * 48

    @Test fun `a new policy holds nothing and a peak without starvation changes nothing`() {
        val p = MediaReservePolicy(48000)
        assertEquals(0, p.heldFrames)
        p.onSupplyPeak(1000, ms(900))
        assertEquals(0, p.heldFrames)
    }

    @Test fun `the first arrival after a starvation sets target plus elapsed time plus pad`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(1000, ms(400))
        p.onArrival(1500)
        assertEquals(ms(400 + 500 + 100), p.heldFrames)
    }

    @Test fun `a second arrival does not add the stall again`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(1000, ms(400))
        p.onArrival(1500)
        p.onArrival(1520)
        assertEquals(ms(1000), p.heldFrames)
    }

    @Test fun `a peak inside the learn window raises the reserve and a later one does not`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(1000, ms(400))
        p.onArrival(1010)
        p.onSupplyPeak(5000, ms(700))
        assertEquals(ms(800), p.heldFrames)
        p.onSupplyPeak(1000 + MediaReservePolicy.LEARN_MS + 1, ms(900))
        assertEquals(ms(800), p.heldFrames)
    }

    @Test fun `a need over the maximum leaves the reserve as it is`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(1000, ms(400))
        p.onArrival(1600)
        val held = p.heldFrames
        p.onStarved(20_000, ms(400))
        p.onArrival(21_500)
        assertEquals(held, p.heldFrames)
        p.onStarved(40_000, ms(400))
        p.onSupplyPeak(41_000, ms(1150))
        assertEquals(held, p.heldFrames)
    }

    @Test fun `a peak of the stall size renews the hold and a smaller one does not`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(0, ms(400))
        p.onArrival(500)
        p.onSupplyPeak(150_000, ms(199))
        p.onSupplyPeak(MediaReservePolicy.HOLD_MS + 1000, ms(10))
        assert(p.heldFrames < ms(1000)) { "held=${p.heldFrames}" }
        val q = MediaReservePolicy(48000)
        q.onStarved(0, ms(400))
        q.onArrival(500)
        q.onSupplyPeak(150_000, ms(200))
        q.onSupplyPeak(MediaReservePolicy.HOLD_MS + 1000, ms(10))
        assertEquals(ms(1000), q.heldFrames)
    }

    @Test fun `after the hold with no stall the reserve falls 5 ms per second to zero`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(0, ms(400))
        p.onArrival(500)
        val start = MediaReservePolicy.HOLD_MS + 1000
        p.onSupplyPeak(start, 0)
        assertEquals(ms(995), p.heldFrames)
        p.onSupplyPeak(start + 1000, 0)
        assertEquals(ms(990), p.heldFrames)
        for (t in 2000L..300_000L step 1000) p.onSupplyPeak(start + t, 0)
        assertEquals(0, p.heldFrames)
    }

    @Test fun `idle moves the hold clock`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(0, ms(400))
        p.onArrival(500)
        p.onIdle(100_000)
        p.onSupplyPeak(MediaReservePolicy.HOLD_MS + 1000, 0)
        assertEquals(ms(1000), p.heldFrames)
    }

    @Test fun `idle moves a pending starvation`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(0, ms(400))
        p.onIdle(500)
        p.onArrival(500)
        assertEquals(ms(400 + 100), p.heldFrames)
    }

    @Test fun `reset clears the reserve and a pending starvation`() {
        val p = MediaReservePolicy(48000)
        p.onStarved(0, ms(400))
        p.onArrival(500)
        p.onStarved(1000, ms(400))
        p.reset()
        p.onArrival(1500)
        assertEquals(0, p.heldFrames)
    }

    @Test fun `a learned reserve is holding until no stall comes for the hold`() {
        val p = MediaReservePolicy(48000)
        assertEquals(false, p.isHolding(1000))
        p.onStarved(1000, ms(400))
        p.onArrival(1500)
        assertEquals(true, p.isHolding(1000 + MediaReservePolicy.HOLD_MS - 1))
        assertEquals(false, p.isHolding(1000 + MediaReservePolicy.HOLD_MS))
    }
}
