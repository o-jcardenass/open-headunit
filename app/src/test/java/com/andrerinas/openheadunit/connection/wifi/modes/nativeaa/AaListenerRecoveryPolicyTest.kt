package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.AaListenerRecoveryPolicy.Exit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaListenerRecoveryPolicyTest {

    @Test
    fun `a handoff close is not a failure`() {
        assertEquals(Exit.HANDOFF, AaListenerRecoveryPolicy.exitKind(closedForSession = true, running = true))
    }

    @Test
    fun `a stopped manager is not a failure`() {
        assertEquals(Exit.STOPPED, AaListenerRecoveryPolicy.exitKind(closedForSession = false, running = false))
    }

    @Test
    fun `a running manager whose loop ended lost its listener`() {
        assertEquals(Exit.FAILED, AaListenerRecoveryPolicy.exitKind(closedForSession = false, running = true))
    }

    @Test
    fun `a deliberate close outranks the stop`() {
        assertEquals(Exit.HANDOFF, AaListenerRecoveryPolicy.exitKind(closedForSession = true, running = false))
    }

    @Test
    fun `a radio that is off is never reopened onto`() {
        assertFalse(AaListenerRecoveryPolicy.mayReopen(adapterEnabled = false, attemptsSoFar = 0))
    }

    @Test
    fun `reopening is bounded`() {
        assertTrue(AaListenerRecoveryPolicy.mayReopen(true, AaListenerRecoveryPolicy.MAX_REOPEN_ATTEMPTS - 1))
        assertFalse(AaListenerRecoveryPolicy.mayReopen(true, AaListenerRecoveryPolicy.MAX_REOPEN_ATTEMPTS))
    }

    @Test
    fun `a radio that is off is asked to come back once, with the option on and a supported system`() {
        assertTrue(AaListenerRecoveryPolicy.enablesAdapter(enabled = false, optionOn = true, supported = true, alreadyAsked = false, nowMs = 100_000L, lastAskedAtMs = null))
    }

    @Test
    fun `every other input leaves the radio alone`() {
        assertFalse(AaListenerRecoveryPolicy.enablesAdapter(enabled = true, optionOn = true, supported = true, alreadyAsked = false, nowMs = 100_000L, lastAskedAtMs = null))
        assertFalse(AaListenerRecoveryPolicy.enablesAdapter(enabled = false, optionOn = false, supported = true, alreadyAsked = false, nowMs = 100_000L, lastAskedAtMs = null))
        assertFalse(AaListenerRecoveryPolicy.enablesAdapter(enabled = false, optionOn = true, supported = false, alreadyAsked = false, nowMs = 100_000L, lastAskedAtMs = null))
        assertFalse(AaListenerRecoveryPolicy.enablesAdapter(enabled = false, optionOn = true, supported = true, alreadyAsked = true, nowMs = 100_000L, lastAskedAtMs = null))
    }

    @Test
    fun `no second re-enable within the gap, one again after it`() {
        val gap = AaListenerRecoveryPolicy.ADAPTER_REENABLE_GAP_MS
        assertFalse(AaListenerRecoveryPolicy.enablesAdapter(false, true, true, false, nowMs = 100_000L, lastAskedAtMs = 100_000L - gap + 1))
        assertTrue(AaListenerRecoveryPolicy.enablesAdapter(false, true, true, false, nowMs = 100_000L, lastAskedAtMs = 100_000L - gap))
    }
}
