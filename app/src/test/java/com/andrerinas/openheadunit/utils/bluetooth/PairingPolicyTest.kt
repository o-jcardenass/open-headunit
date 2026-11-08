package com.andrerinas.openheadunit.utils.bluetooth

import com.andrerinas.openheadunit.utils.bluetooth.PairingPolicy.BondEvent
import com.andrerinas.openheadunit.utils.bluetooth.PairingPolicy.Prerequisite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPolicyTest {

    private fun missing(sdk: Int, btOn: Boolean = true, nearby: Boolean = true, fine: Boolean = true,
                        locationOn: Boolean = true, visible: Boolean = true) =
        PairingPolicy.prerequisites(sdk, btOn, nearby, fine, locationOn, visible)

    @Test
    fun `the nearby item shows from api 31 only`() {
        assertEquals(emptyList<Prerequisite>(), missing(30, nearby = false))
        assertEquals(listOf(Prerequisite.NEARBY_DEVICES), missing(31, nearby = false))
    }

    @Test
    fun `the location items show from api 23 at every level above`() {
        for (sdk in listOf(23, 29, 31)) {
            assertEquals(listOf(Prerequisite.FINE_LOCATION), missing(sdk, fine = false))
            assertEquals(listOf(Prerequisite.LOCATION_ON), missing(sdk, locationOn = false))
        }
        assertEquals(emptyList<Prerequisite>(), missing(22, fine = false, locationOn = false))
    }

    @Test
    fun `bluetooth off comes first and visible shows at every level`() {
        assertEquals(listOf(Prerequisite.BLUETOOTH_ON, Prerequisite.VISIBLE), missing(19, btOn = false, visible = false))
        assertEquals(listOf(Prerequisite.VISIBLE), missing(34, visible = false))
    }

    @Test
    fun `every item unmet at api 31 keeps the order`() {
        assertEquals(
            listOf(
                Prerequisite.BLUETOOTH_ON, Prerequisite.NEARBY_DEVICES, Prerequisite.FINE_LOCATION,
                Prerequisite.LOCATION_ON, Prerequisite.VISIBLE
            ),
            missing(31, btOn = false, nearby = false, fine = false, locationOn = false, visible = false)
        )
    }

    @Test
    fun `bond transitions map to row events`() {
        val none = PairingPolicy.BOND_NONE
        val bonding = PairingPolicy.BOND_BONDING
        val bonded = PairingPolicy.BOND_BONDED
        assertEquals(BondEvent.ASKING, PairingPolicy.bondTransition(none, bonding))
        assertEquals(BondEvent.FAILED, PairingPolicy.bondTransition(bonding, none))
        assertEquals(BondEvent.DONE, PairingPolicy.bondTransition(bonding, bonded))
        assertEquals(BondEvent.DONE, PairingPolicy.bondTransition(none, bonded))
        for (s in listOf(none, bonding, bonded)) assertEquals(BondEvent.IGNORE, PairingPolicy.bondTransition(s, s))
    }

    @Test
    fun `kind is phone only for the phone major class`() {
        assertEquals(PairingPolicy.Kind.PHONE, PairingPolicy.kindOf(0x0200))
        assertEquals(PairingPolicy.Kind.OTHER, PairingPolicy.kindOf(0x0100))
        assertEquals(PairingPolicy.Kind.OTHER, PairingPolicy.kindOf(0x0400))
        assertEquals(PairingPolicy.Kind.OTHER, PairingPolicy.kindOf(null))
    }

    @Test
    fun `merge keeps one row per address in first seen order`() {
        var list = emptyList<PairingRow>()
        list = PairingPolicy.mergeFound(list, FoundDevice("A", "one", 0x0200, false))
        list = PairingPolicy.mergeFound(list, FoundDevice("B", "two", null, false))
        list = PairingPolicy.mergeFound(list, FoundDevice("A", "one", 0x0200, false))
        assertEquals(listOf("A", "B"), list.map { it.address })
        list = PairingPolicy.mergeFound(list, FoundDevice("A", "renamed", 0x0200, false))
        assertEquals(listOf("renamed", "two"), list.map { it.name })
        assertEquals(2, list.size)
    }

    @Test
    fun `a bonded device is not added`() {
        assertTrue(PairingPolicy.mergeFound(emptyList(), FoundDevice("A", "one", 0x0200, true)).isEmpty())
    }

    @Test
    fun `visible time never goes below zero`() {
        assertEquals(120, PairingPolicy.VISIBLE_SECONDS)
        assertEquals(120_000L, PairingPolicy.remainingMs(1_000, 120, 1_000))
        assertEquals(60_000L, PairingPolicy.remainingMs(1_000, 120, 61_000))
        assertEquals(0L, PairingPolicy.remainingMs(1_000, 120, 121_000))
        assertEquals(0L, PairingPolicy.remainingMs(1_000, 120, 500_000))
    }

    @Test
    fun `the visible route follows the prompt`() {
        assertEquals(PairingPolicy.VisibleRoute.SYSTEM_PROMPT, PairingPolicy.visibleRoute(true))
        assertEquals(PairingPolicy.VisibleRoute.NONE, PairingPolicy.visibleRoute(false))
    }

    @Test
    fun `a fresh search is for api 26 and up when the last end is old or unknown`() {
        assertFalse(PairingPolicy.needsFreshSearch(25, null))
        for (sdk in listOf(26, 34)) {
            assertTrue(PairingPolicy.needsFreshSearch(sdk, 60_001))
            assertTrue(PairingPolicy.needsFreshSearch(sdk, null))
            assertFalse(PairingPolicy.needsFreshSearch(sdk, 59_999))
        }
    }

    @Test
    fun `a fresh prompt is for api 19 to 25 when the last grant is old or unknown`() {
        for (sdk in listOf(19, 25)) {
            assertTrue(PairingPolicy.needsFreshPrompt(sdk, 180_001, 120))
            assertTrue(PairingPolicy.needsFreshPrompt(sdk, null, 120))
            assertFalse(PairingPolicy.needsFreshPrompt(sdk, 179_999, 120))
        }
        for (sdk in listOf(18, 26)) {
            assertFalse(PairingPolicy.needsFreshPrompt(sdk, 999_999, 120))
            assertFalse(PairingPolicy.needsFreshPrompt(sdk, null, 120))
        }
    }

    @Test
    fun `the asking hint comes after 20 seconds`() {
        assertFalse(PairingPolicy.askingHint(19_999))
        assertTrue(PairingPolicy.askingHint(20_001))
    }

    private fun entry(sdk: Int = 34, external: Boolean = false, blocked: Boolean = false,
                      radio: String = "", adapter: Boolean = true, session: Boolean = false) =
        PairingPolicy.entryAvailable(sdk, external, blocked, radio, adapter, session)

    @Test
    fun `the entry needs every input to allow it`() {
        assertTrue(entry())
        assertTrue(entry(sdk = 19))
        assertFalse(entry(sdk = 18))
        assertFalse(entry(external = true))
        assertFalse(entry(blocked = true))
        assertTrue(entry(radio = "bluetooth_manager"))
        assertFalse(entry(radio = "bluetooth_manager2"))
        assertFalse(entry(adapter = false))
        assertFalse(entry(session = true))
    }

    @Test
    fun `only a missing activity latches the prompt route`() {
        assertTrue(PairingPolicy.latchesPromptUnavailable(PairingPolicy.PromptLaunch.NO_ACTIVITY))
        assertFalse(PairingPolicy.latchesPromptUnavailable(PairingPolicy.PromptLaunch.REFUSED))
        assertFalse(PairingPolicy.latchesPromptUnavailable(PairingPolicy.PromptLaunch.OK))
    }

    @Test
    fun `settings stay held while visible or bonding`() {
        assertTrue(PairingPolicy.holdsSettings(1, false))
        assertFalse(PairingPolicy.holdsSettings(0, false))
        assertTrue(PairingPolicy.holdsSettings(0, true))
    }
}
