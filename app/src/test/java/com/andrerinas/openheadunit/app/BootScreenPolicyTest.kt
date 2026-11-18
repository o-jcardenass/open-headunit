package com.andrerinas.openheadunit.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootScreenPolicyTest {

    @Test
    fun `start on boot opens the screen by default`() {
        assertTrue(BootScreenPolicy.opensScreen(startOnBoot = true, waitInBackground = false, homeScreenAutoConnect = false))
    }

    @Test
    fun `waiting in the background keeps the screen closed`() {
        assertFalse(BootScreenPolicy.opensScreen(startOnBoot = true, waitInBackground = true, homeScreenAutoConnect = false))
    }

    @Test
    fun `waiting in the background still opens the screen when an auto-connect needs it`() {
        assertTrue(BootScreenPolicy.opensScreen(startOnBoot = true, waitInBackground = true, homeScreenAutoConnect = true))
    }

    @Test
    fun `nothing opens when start on boot is off`() {
        for (wait in listOf(false, true)) for (auto in listOf(false, true)) {
            assertFalse(BootScreenPolicy.opensScreen(startOnBoot = false, waitInBackground = wait, homeScreenAutoConnect = auto))
        }
    }

    @Test
    fun `self mode and the wifi server reconnect count as home screen auto-connect`() {
        assertFalse(BootScreenPolicy.homeScreenAutoConnect(false, false, false, false))
        assertTrue(BootScreenPolicy.homeScreenAutoConnect(true, false, false, false))
        assertTrue(BootScreenPolicy.homeScreenAutoConnect(false, lastSession = true, lastWasWifi = true, wifiServerMode = true))
    }

    @Test
    fun `a last session over usb or another wireless mode runs from the service and keeps the screen closed`() {
        assertFalse(BootScreenPolicy.homeScreenAutoConnect(false, lastSession = true, lastWasWifi = false, wifiServerMode = true))
        assertFalse(BootScreenPolicy.homeScreenAutoConnect(false, lastSession = true, lastWasWifi = true, wifiServerMode = false))
        assertFalse(BootScreenPolicy.homeScreenAutoConnect(false, lastSession = false, lastWasWifi = true, wifiServerMode = true))
    }
}
