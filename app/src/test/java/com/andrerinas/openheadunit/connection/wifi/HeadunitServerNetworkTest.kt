package com.andrerinas.openheadunit.connection.wifi

import com.andrerinas.openheadunit.connection.wifi.HeadunitServerNetwork.PHONE_HOTSPOT
import com.andrerinas.openheadunit.connection.wifi.HeadunitServerNetwork.SHARED
import com.andrerinas.openheadunit.connection.wifi.HeadunitServerNetwork.UNIT_HOTSPOT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadunitServerNetworkTest {

    @Test
    fun `a unit that never chose keeps what it had`() {
        assertEquals(SHARED, HeadunitServerNetwork.byIdOrDefault(-1, autoEnableHotspot = false))
        assertEquals("the app already drives its access point", UNIT_HOTSPOT,
            HeadunitServerNetwork.byIdOrDefault(-1, autoEnableHotspot = true))
        assertEquals(SHARED, HeadunitServerNetwork.byIdOrDefault(99, autoEnableHotspot = false))
    }

    @Test
    fun `a choice once made wins over the hotspot switch`() {
        for (n in HeadunitServerNetwork.entries) {
            assertEquals(n, HeadunitServerNetwork.byIdOrDefault(n.id, autoEnableHotspot = true))
            assertEquals(n, HeadunitServerNetwork.byIdOrDefault(n.id, autoEnableHotspot = false))
        }
    }

    @Test
    fun `ids are stored, so they never move`() {
        assertEquals(0, SHARED.id)
        assertEquals(1, PHONE_HOTSPOT.id)
        assertEquals(2, UNIT_HOTSPOT.id)
    }

    @Test
    fun `a Save stores the network only where it was shown or changed`() {
        // The AUTO page showed SHARED; Auto-Enable moved the default to UNIT_HOTSPOT: keep the shown choice.
        assertTrue(HeadunitServerNetwork.saveStores(SHARED, SHARED, UNIT_HOTSPOT, pickerShown = true))
        assertFalse(HeadunitServerNetwork.saveStores(SHARED, SHARED, SHARED, pickerShown = true))
        // A Native AA or Helper page never showed it: the moved default stays live.
        assertFalse(HeadunitServerNetwork.saveStores(SHARED, SHARED, UNIT_HOTSPOT, pickerShown = false))
        assertFalse(HeadunitServerNetwork.saveStores(SHARED, null, SHARED, pickerShown = false))
        assertTrue(HeadunitServerNetwork.saveStores(PHONE_HOTSPOT, SHARED, SHARED, pickerShown = false))
    }

    @Test
    fun `the sweep waits only on the phone's hotspot with no WiFi joined`() {
        assertTrue(HeadunitServerNetwork.sweepWaitsForWifi(WifiLauncherMode.AUTO, PHONE_HOTSPOT, wifiJoined = false, unitHotspotUp = { false }))
        assertFalse(HeadunitServerNetwork.sweepWaitsForWifi(WifiLauncherMode.AUTO, PHONE_HOTSPOT, wifiJoined = true, unitHotspotUp = { false }))
    }

    @Test
    fun `the unit's own access point sweeps on the phone's hotspot too`() {
        // The phone can join this unit's AP whatever was chosen; waiting would never find it.
        assertFalse(HeadunitServerNetwork.sweepWaitsForWifi(WifiLauncherMode.AUTO, PHONE_HOTSPOT, wifiJoined = false, unitHotspotUp = { true }))
    }

    @Test
    fun `the other networks and every other mode sweep as they always did`() {
        // The access point is not read where the answer cannot depend on it.
        val apNotRead: () -> Boolean = { throw AssertionError("unitHotspotUp read") }
        for (joined in listOf(true, false)) {
            assertFalse(HeadunitServerNetwork.sweepWaitsForWifi(WifiLauncherMode.AUTO, SHARED, joined, apNotRead))
            // Its own access point leaves the unit on no WiFi network: waiting would never end.
            assertFalse(HeadunitServerNetwork.sweepWaitsForWifi(WifiLauncherMode.AUTO, UNIT_HOTSPOT, joined, apNotRead))
            for (mode in WifiLauncherMode.entries.filter { it != WifiLauncherMode.AUTO }) {
                for (n in HeadunitServerNetwork.entries) {
                    assertFalse("$mode $n", HeadunitServerNetwork.sweepWaitsForWifi(mode, n, joined, apNotRead))
                }
            }
        }
    }
}
