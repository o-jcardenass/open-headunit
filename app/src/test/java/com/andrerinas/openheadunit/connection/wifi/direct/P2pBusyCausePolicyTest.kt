package com.andrerinas.openheadunit.connection.wifi.direct

import com.andrerinas.openheadunit.connection.wifi.direct.P2pBusyCausePolicy.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class P2pBusyCausePolicyTest {
    private val busy = P2pCreateWedgePolicy.BUSY

    @Test
    fun busyWithApUpFreesTheRadio() {
        assertEquals(Step.FREE_RADIO, P2pBusyCausePolicy.stepAfterBusy(busy, true, true, false))
    }

    @Test
    fun spentTeardownLeavesTheLadder() {
        assertEquals(Step.LADDER, P2pBusyCausePolicy.stepAfterBusy(busy, true, true, true))
    }

    @Test
    fun disallowedTeardownLeavesTheLadder() {
        assertEquals(Step.LADDER, P2pBusyCausePolicy.stepAfterBusy(busy, true, false, false))
    }

    @Test
    fun busyWithApDownLeavesTheLadder() {
        assertEquals(Step.LADDER, P2pBusyCausePolicy.stepAfterBusy(busy, false, true, false))
    }

    @Test
    fun otherReasonsLeaveTheLadder() {
        assertEquals(Step.LADDER, P2pBusyCausePolicy.stepAfterBusy(0, true, true, false))
        assertEquals(Step.LADDER, P2pBusyCausePolicy.stepAfterBusy(1, true, true, false))
    }

    @Test
    fun keysOnTheSameConstantAsTheWedgePolicy() {
        assertEquals(2, P2pCreateWedgePolicy.BUSY)
        assertEquals(Step.FREE_RADIO, P2pBusyCausePolicy.stepAfterBusy(P2pCreateWedgePolicy.BUSY, true, true, false))
    }
}
