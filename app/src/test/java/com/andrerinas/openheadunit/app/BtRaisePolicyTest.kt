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
    ) = BtRaisePolicy.decide(
        settingOn, macInAutoStartList, sessionLive, appVisible, settingsVisible,
        userExitedToOemLauncher, cancelledByUser, sinceLastRaiseMs,
    )

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
