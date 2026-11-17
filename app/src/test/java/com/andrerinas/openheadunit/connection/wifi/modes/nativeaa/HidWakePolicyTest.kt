package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidWakePolicyTest {

    private fun may(
        settingOn: Boolean = true,
        apiLevel: Int = 28,
        mode: WifiLauncherMode = WifiLauncherMode.NATIVE,
        bonded: Boolean = true,
        inWakeList: Boolean = true,
        sessionLive: Boolean = false,
        standDown: Boolean = false,
        wakesInWindow: Int = 0,
    ) = HidWakePolicy.mayWake(
        settingOn, apiLevel, mode, bonded, inWakeList, sessionLive, standDown, wakesInWindow
    )

    @Test fun `the allowed case wakes`() = assertTrue(may())

    @Test fun `setting off does not wake`() = assertFalse(may(settingOn = false))

    @Test fun `below API 28 does not wake`() = assertFalse(may(apiLevel = 27))

    @Test fun `a mode other than Native does not wake`() {
        for (mode in WifiLauncherMode.entries) {
            if (mode != WifiLauncherMode.NATIVE) assertFalse(mode.name, may(mode = mode))
        }
    }

    @Test fun `an unbonded phone does not wake`() = assertFalse(may(bonded = false))

    @Test fun `a phone outside the wake list does not wake`() = assertFalse(may(inWakeList = false))

    @Test fun `a live session does not wake`() = assertFalse(may(sessionLive = true))

    @Test fun `a stand-down does not wake`() = assertFalse(may(standDown = true))

    @Test fun `a spent budget does not wake`() {
        assertTrue(may(wakesInWindow = HidWakePolicy.MAX_WAKES_IN_WINDOW - 1))
        assertFalse(may(wakesInWindow = HidWakePolicy.MAX_WAKES_IN_WINDOW))
    }

    @Test fun `only wakes inside the window count`() {
        val now = 1_000_000L
        val stamps = listOf(now - HidWakePolicy.WINDOW_MS - 1, now - HidWakePolicy.WINDOW_MS + 1, now)
        assertEquals(2, HidWakePolicy.countInWindow(stamps, now))
    }
}
