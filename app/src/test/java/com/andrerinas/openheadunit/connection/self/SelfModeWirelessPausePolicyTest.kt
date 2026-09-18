package com.andrerinas.openheadunit.connection.self

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfModeWirelessPausePolicyTest {

    private fun refuses(
        selfModeArmed: Boolean = false,
        loopbackSessionLive: Boolean = false,
        userRequested: Boolean = false
    ) = SelfModeWirelessPausePolicy.refusesBringUp(
        selfModeArmed = selfModeArmed,
        loopbackSessionLive = loopbackSessionLive,
        userRequested = userRequested
    )

    @Test
    fun `an armed launch refuses an automatic bring-up`() {
        // The launchers have not finished, so nothing should create a group underneath them.
        assertTrue(refuses(selfModeArmed = true))
    }

    @Test
    fun `a live loopback session refuses an automatic bring-up`() {
        // Covers a launch whose own timeout cleared the flag while the session it started ran on.
        assertTrue(refuses(loopbackSessionLive = true))
    }

    @Test
    fun `the WiFi button lifts it in both states`() {
        assertFalse(refuses(selfModeArmed = true, userRequested = true))
        assertFalse(refuses(loopbackSessionLive = true, userRequested = true))
    }

    @Test
    fun `nothing is refused once Self Mode is clear`() {
        // The re-arm after a Self Mode session ends has to get through here, or the wireless stack
        // stays down for the rest of the process.
        assertFalse(refuses())
        assertFalse(refuses(userRequested = true))
    }
}
