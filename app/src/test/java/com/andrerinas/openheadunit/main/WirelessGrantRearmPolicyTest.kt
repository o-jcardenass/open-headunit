package com.andrerinas.openheadunit.main

import android.Manifest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessGrantRearmPolicyTest {

    @Test
    fun `each Bluetooth or Nearby permission re-arms a wireless mode`() {
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        ).forEach { assertTrue(it, WirelessGrantRearmPolicy.rearms(listOf(it), wirelessOn = true, sdkInt = 33)) }
    }

    @Test
    fun `an unrelated grant or a mode that is not wireless does not`() {
        assertFalse(WirelessGrantRearmPolicy.rearms(listOf(Manifest.permission.POST_NOTIFICATIONS), wirelessOn = true, sdkInt = 33))
        assertFalse(WirelessGrantRearmPolicy.rearms(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), wirelessOn = true, sdkInt = 33))
        // From API 33 Nearby devices replaces location for the wireless modes.
        assertFalse(WirelessGrantRearmPolicy.rearms(listOf(Manifest.permission.ACCESS_FINE_LOCATION), wirelessOn = true, sdkInt = 33))
        assertFalse(WirelessGrantRearmPolicy.rearms(listOf(Manifest.permission.BLUETOOTH_CONNECT), wirelessOn = false, sdkInt = 33))
        assertFalse(WirelessGrantRearmPolicy.rearms(emptyList(), wirelessOn = true, sdkInt = 33))
    }

    @Test
    fun `one wireless permission among others is enough`() {
        assertTrue(WirelessGrantRearmPolicy.rearms(
            listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_SCAN), wirelessOn = true, sdkInt = 33))
    }

    @Test
    fun `the re-arm is forced only when asked and no session is live`() {
        assertTrue(WirelessGrantRearmPolicy.forces(requested = true, sessionLive = false))
        assertFalse(WirelessGrantRearmPolicy.forces(requested = true, sessionLive = true))
        assertFalse(WirelessGrantRearmPolicy.forces(requested = false, sessionLive = false))
    }

    @Test
    fun `below API 33 a fine location grant re-arms, from 33 it does not`() {
        val fine = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(WirelessGrantRearmPolicy.rearms(fine, wirelessOn = true, sdkInt = 29))
        assertTrue(WirelessGrantRearmPolicy.rearms(fine, wirelessOn = true, sdkInt = 32))
        assertFalse(WirelessGrantRearmPolicy.rearms(fine, wirelessOn = true, sdkInt = 33))
        assertFalse(WirelessGrantRearmPolicy.rearms(fine, wirelessOn = false, sdkInt = 29))
    }
}
