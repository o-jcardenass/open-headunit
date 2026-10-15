package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.HotspotStartSupersededPolicy.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotStartSupersededPolicyTest {

    @Test
    fun beforeRequestCarriesOnOnSameGeneration() {
        assertEquals(Step.CARRY_ON, HotspotStartSupersededPolicy.beforeRequest(3, 3))
    }

    @Test
    fun beforeRequestAbandonsOnMovedGeneration() {
        assertEquals(Step.ABANDON, HotspotStartSupersededPolicy.beforeRequest(3, 4))
    }

    @Test
    fun keepsWaitingOnlyOnSameGeneration() {
        assertTrue(HotspotStartSupersededPolicy.keepsWaiting(2, 2))
        assertFalse(HotspotStartSupersededPolicy.keepsWaiting(2, 3))
    }

    @Test
    fun afterWaitCarriesOnWhenNoStopCame() {
        for (up in listOf(true, false)) for (attempted in listOf(true, false)) {
            assertEquals(Step.CARRY_ON, HotspotStartSupersededPolicy.afterWait(1, 1, up, attempted))
        }
    }

    @Test
    fun afterWaitTakesDownAnApThatIsUp() {
        assertEquals(Step.TAKE_DOWN, HotspotStartSupersededPolicy.afterWait(1, 2, apUp = true, attempted = true))
    }

    @Test
    fun afterWaitTakesDownWhenARequestWentOutEvenIfApIsNotUpYet() {
        assertEquals(Step.TAKE_DOWN, HotspotStartSupersededPolicy.afterWait(1, 2, apUp = false, attempted = true))
    }

    @Test
    fun afterWaitAbandonsWhenNothingWasAsked() {
        assertEquals(Step.ABANDON, HotspotStartSupersededPolicy.afterWait(1, 2, apUp = false, attempted = false))
    }

    @Test
    fun restartReenablesOnlyWhenNoLaterStopCame() {
        assertTrue(HotspotStartSupersededPolicy.restartReenables(5, 5))
        assertFalse(HotspotStartSupersededPolicy.restartReenables(5, 6))
    }

    @Test
    fun drainWaitsOnlyForAStartAndBelowTheCeiling() {
        val ceiling = HotspotStartSupersededPolicy.START_DRAIN_CEILING_MS
        assertFalse(HotspotStartSupersededPolicy.drainsStart(false, 0))
        assertTrue(HotspotStartSupersededPolicy.drainsStart(true, ceiling - 1))
        assertFalse(HotspotStartSupersededPolicy.drainsStart(true, ceiling))
    }

    /** Models the AP: true when a request raised it and no take-down followed. */
    private class Model {
        var generation = 0
        var apUp = false
        var requested = false
    }

    private fun runStart(m: Model, startedAt: Int, stopBeforeBand: Boolean, stopAfterRequest: Boolean, apComes: Boolean) {
        if (stopBeforeBand) m.generation++
        if (HotspotStartSupersededPolicy.beforeRequest(startedAt, m.generation) != Step.CARRY_ON) return
        m.requested = true
        if (stopAfterRequest) m.generation++
        if (apComes) m.apUp = true
        val step = HotspotStartSupersededPolicy.afterWait(startedAt, m.generation, m.apUp, true)
        if (step == Step.TAKE_DOWN) m.apUp = false
    }

    @Test
    fun interleavingNoStopLeavesApUp() {
        val m = Model()
        runStart(m, 0, stopBeforeBand = false, stopAfterRequest = false, apComes = true)
        assertTrue(m.apUp)
    }

    @Test
    fun interleavingStopDuringWifiWaitSendsNoRequest() {
        val m = Model()
        runStart(m, 0, stopBeforeBand = true, stopAfterRequest = false, apComes = true)
        assertFalse(m.requested)
        assertFalse(m.apUp)
    }

    @Test
    fun interleavingStopAfterRequestEndsDownWhenApCame() {
        val m = Model()
        runStart(m, 0, stopBeforeBand = false, stopAfterRequest = true, apComes = true)
        assertFalse(m.apUp)
    }

    @Test
    fun interleavingStopAfterRequestSendsTakeDownEvenIfApNeverCame() {
        assertEquals(
            Step.TAKE_DOWN,
            HotspotStartSupersededPolicy.afterWait(0, 1, apUp = false, attempted = true)
        )
    }

    @Test
    fun interleavingRestartReenableSkippedAfterSecondStop() {
        var generation = 0
        generation++
        val own = generation
        generation++
        assertFalse(HotspotStartSupersededPolicy.restartReenables(own, generation))
    }

    @Test
    fun interleavingRestartReenableRunsWithoutAnotherStop() {
        var generation = 0
        generation++
        assertTrue(HotspotStartSupersededPolicy.restartReenables(generation, generation))
    }
}
