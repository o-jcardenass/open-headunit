package com.andrerinas.openheadunit.app

import com.andrerinas.openheadunit.app.BtRaisePolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class BtRaisePolicyTest {

    private fun decide(
        settingOn: Boolean = true,
        macInAutoStartList: Boolean = true,
        sessionLive: Boolean = false,
        appVisible: Boolean = false,
        settingsVisible: Boolean = false,
        userExitedToOemLauncher: Boolean = false,
        cancelledByUser: Boolean = false,
        sinceLastRaiseMs: Long? = null,
        pokeInFlight: Boolean = false,
    ) = BtRaisePolicy.decide(
        settingOn, macInAutoStartList, sessionLive, appVisible, settingsVisible,
        userExitedToOemLauncher, cancelledByUser, sinceLastRaiseMs, pokeInFlight,
    )

    @Test
    fun `our own poke's arrival returns none`() {
        assertEquals(Action.NONE, decide(pokeInFlight = true))
        assertEquals(Action.NONE, decide(pokeInFlight = true, sessionLive = true))
    }

    @Test
    fun `a raise that landed needs no fallback even if the user left again`() {
        assertEquals(false, BtRaisePolicy.needsFallback(lastForegroundAtMs = 1_500, raisedAtMs = 1_000, checkDeadlineMs = 3_000, nowMs = 3_000))
    }

    @Test
    fun `a dropped raise needs the fallback`() {
        assertEquals(true, BtRaisePolicy.needsFallback(lastForegroundAtMs = 500, raisedAtMs = 1_000, checkDeadlineMs = 3_000, nowMs = 3_010))
    }

    @Test
    fun `a check that slept past its deadline is stale`() {
        assertEquals(false, BtRaisePolicy.needsFallback(lastForegroundAtMs = 0, raisedAtMs = 1_000, checkDeadlineMs = 3_000, nowMs = 3_000 + 3_600_000))
    }

    @Test
    fun `setting off returns none in every state`() {
        for (live in listOf(false, true)) for (visible in listOf(false, true)) for (oem in listOf(false, true)) {
            assertEquals(Action.NONE, decide(settingOn = false, sessionLive = live, appVisible = visible, userExitedToOemLauncher = oem))
        }
    }

    @Test
    fun `a phone outside the auto-start list returns none`() {
        assertEquals(Action.NONE, decide(macInAutoStartList = false))
        assertEquals(Action.NONE, decide(macInAutoStartList = false, sessionLive = true))
    }

    @Test
    fun `no session raises the home screen`() {
        assertEquals(Action.RAISE_HOME, decide())
    }

    @Test
    fun `live session in the background raises the projection`() {
        assertEquals(Action.RAISE_PROJECTION, decide(sessionLive = true))
    }

    @Test
    fun `exit to the car launcher blocks the projection raise`() {
        assertEquals(Action.NONE, decide(sessionLive = true, userExitedToOemLauncher = true))
    }

    @Test
    fun `settings screen visible returns none`() {
        assertEquals(Action.NONE, decide(settingsVisible = true))
        assertEquals(Action.NONE, decide(settingsVisible = true, sessionLive = true))
    }

    @Test
    fun `status pill cancel returns none`() {
        assertEquals(Action.NONE, decide(cancelledByUser = true))
    }

    @Test
    fun `a visible app returns none`() {
        assertEquals(Action.NONE, decide(appVisible = true))
        assertEquals(Action.NONE, decide(appVisible = true, sessionLive = true))
    }

    @Test
    fun `a second raise inside ten seconds returns none`() {
        assertEquals(Action.NONE, decide(sinceLastRaiseMs = 9_999L))
        assertEquals(Action.RAISE_HOME, decide(sinceLastRaiseMs = 10_000L))
    }
}
