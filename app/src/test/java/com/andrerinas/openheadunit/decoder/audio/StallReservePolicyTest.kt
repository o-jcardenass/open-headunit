package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class StallReservePolicyTest {
    private val policy = StallReservePolicy()

    @Test fun `normal jitter holds nothing`() {
        policy.onSupplyGap(1_000, 450)
        assertEquals(0L, policy.reserveMs)
    }

    @Test fun `a scan stall holds the stall plus the margin`() {
        policy.onSupplyGap(1_000, 1_470)
        assertEquals(1_770L, policy.reserveMs)
    }

    @Test fun `a shorter stall keeps the longer reserve`() {
        policy.onSupplyGap(1_000, 1_600)
        policy.onSupplyGap(6_000, 1_400)
        assertEquals(1_900L, policy.reserveMs)
    }

    @Test fun `a very long stall is capped below the bank`() {
        policy.onSupplyGap(1_000, 5_000)
        assertEquals(StallReservePolicy.MAX_RESERVE_MS, policy.reserveMs)
    }

    @Test fun `the reserve releases slowly after ten quiet seconds`() {
        policy.onSupplyGap(1_000, 1_470)
        policy.release(10_999)
        assertEquals(1_770L, policy.reserveMs)
        policy.release(21_000)
        assertEquals(1_720L, policy.reserveMs)
        policy.release(1_000_000)
        assertEquals(0L, policy.reserveMs)
    }

    @Test fun `a new stall restarts the hold`() {
        policy.onSupplyGap(1_000, 1_470)
        policy.release(31_000)
        policy.onSupplyGap(31_000, 1_470)
        policy.release(40_000)
        assertEquals(1_770L, policy.reserveMs)
    }

    @Test fun `a held media reserve keeps a recent stall at its depth`() {
        policy.onSupplyGap(1_000, 960)
        policy.release(150_000, mediaHeld = true)
        assertEquals(1_260L, policy.reserveMs)
    }

    @Test fun `the media hold keeps the depth only for its own hold after the stall`() {
        policy.onSupplyGap(1_000, 960)
        for (now in 2_000L..MediaReservePolicy.HOLD_MS + 20_000 step 1_000) policy.release(now, mediaHeld = true)
        assertEquals(1_160L, policy.reserveMs)
    }
}
