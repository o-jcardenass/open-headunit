package com.andrerinas.openheadunit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOnRaisePolicyTest {

    private val P = ScreenOnRaisePolicy

    @Test
    fun `below API 33 the wake lock alone lights the panel`() {
        for (lit in listOf(true, false, null)) for (session in listOf(true, false)) {
            assertEquals(ScreenOnRaise.NONE, P.decide(32, lit, session))
        }
    }

    @Test
    fun `a lit panel raises nothing, so a projecting session keeps its screen`() {
        assertEquals(ScreenOnRaise.NONE, P.decide(33, interactive = true, sessionConnected = true))
        assertEquals(ScreenOnRaise.NONE, P.decide(34, interactive = true, sessionConnected = false))
    }

    @Test
    fun `a dark panel with a session raises the projection, never the home screen`() {
        assertEquals(ScreenOnRaise.PROJECTION, P.decide(33, interactive = false, sessionConnected = true))
        assertEquals(ScreenOnRaise.PROJECTION, P.decide(35, interactive = null, sessionConnected = true))
    }

    @Test
    fun `a dark panel with no session raises the home screen`() {
        assertEquals(ScreenOnRaise.HOME, P.decide(33, interactive = false, sessionConnected = false))
        assertEquals(ScreenOnRaise.HOME, P.decide(36, interactive = null, sessionConnected = false))
    }
}
