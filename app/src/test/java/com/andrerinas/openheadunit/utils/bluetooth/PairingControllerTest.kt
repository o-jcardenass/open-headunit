package com.andrerinas.openheadunit.utils.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingControllerTest {

    private class FakeAdapter : PairingAdapter {
        val calls = mutableListOf<String>()
        var sink: PairingAdapter.Events? = null
        var discoveryOk = true
        var bondOk = true
        val bondStates = HashMap<String, Int>()
        override fun registerEvents(sink: PairingAdapter.Events) { this.sink = sink; calls += "register" }
        override fun unregisterEvents() { sink = null; calls += "unregister" }
        override fun startDiscovery(): Boolean { calls += "startDiscovery"; return discoveryOk }
        override fun cancelDiscovery() { calls += "cancelDiscovery" }
        override fun isDiscovering() = false
        override fun isVisible() = false
        override fun createBond(address: String): Boolean { calls += "createBond $address"; return bondOk }
        override fun bondState(address: String) = bondStates[address] ?: PairingPolicy.BOND_NONE
    }

    private class FakeListener : PairingController.Listener {
        val events = mutableListOf<String>()
        val searches = mutableListOf<String>()
        var rows: List<PairingRow> = emptyList()
        override fun onRowsChanged(rows: List<PairingRow>) { this.rows = rows }
        override fun onSearchChanged(searching: Boolean, found: Int) { searches += "search $searching" }
        override fun onPromptNeeded() { events += "prompt" }
        override fun onPrerequisitesChanged() { events += "prereq" }
        override fun onSearchFailed() { events += "search failed" }
        override fun onHoldChanged(held: Boolean) { events += "hold $held" }
        override fun onAskingHint(address: String) { events += "hint $address" }
        override fun onBondDone(address: String) { events += "done $address" }
    }

    private class Rig(sdk: Int) {
        val adapter = FakeAdapter()
        val listener = FakeListener()
        val lines = mutableListOf<String>()
        var now = 1_000_000L
        val controller = PairingController(adapter, sdk, listener, { now }, { lines += it })
        fun count(text: String) = lines.count { it == "PairingController: $text" }
        fun state(address: String) = listener.rows.first { it.address == address }.state
    }

    private val none = PairingPolicy.BOND_NONE
    private val bonding = PairingPolicy.BOND_BONDING
    private val bonded = PairingPolicy.BOND_BONDED

    private fun foundAndSearched(sdk: Int, address: String = "AA"): Rig {
        val rig = Rig(sdk)
        rig.controller.start()
        rig.controller.search()
        rig.controller.onFound(address, "phone", 0x0200)
        return rig
    }

    @Test
    fun `start registers once and stop unregisters once`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.start()
        rig.controller.onStop()
        assertEquals(listOf("register"), rig.adapter.calls)
        rig.controller.stop()
        rig.controller.stop()
        assertEquals(listOf("register", "unregister"), rig.adapter.calls)
        assertEquals(1, rig.count("stopped"))
        rig.controller.start()
        assertEquals(2, rig.adapter.calls.count { it == "register" })
    }

    @Test
    fun `onStart reads the bond of a pending address`() {
        val rig = foundAndSearched(34)
        rig.controller.tap("AA")
        rig.adapter.bondStates["AA"] = bonded
        rig.controller.onStart()
        assertEquals(RowState.DONE, rig.state("AA"))

        val other = foundAndSearched(34)
        other.controller.tap("AA")
        other.adapter.bondStates["AA"] = bonding
        other.controller.onStart()
        assertEquals(RowState.ASKING, other.state("AA"))
    }

    @Test
    fun `a bond read of none fails only a row that was asking`() {
        val idle = foundAndSearched(34)
        idle.controller.tap("AA")
        idle.controller.onStart()
        assertEquals(RowState.IDLE, idle.state("AA"))

        val asking = foundAndSearched(34)
        asking.controller.tap("AA")
        asking.controller.onBondChanged("AA", none, bonding)
        asking.controller.onStart()
        assertEquals(RowState.FAILED, asking.state("AA"))
    }

    @Test
    fun `a tap during a search cancels before it bonds`() {
        val rig = foundAndSearched(25)
        rig.controller.onPromptResult(120)
        rig.adapter.calls.clear()
        rig.controller.tap("AA")
        assertEquals(listOf("cancelDiscovery", "createBond AA"), rig.adapter.calls)
    }

    @Test
    fun `onStop cancels a running search and onStart restarts it`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.search()
        rig.controller.onStop()
        assertEquals("cancelDiscovery", rig.adapter.calls.last())
        rig.controller.onStart()
        assertEquals("startDiscovery", rig.adapter.calls.last())

        val idle = Rig(34)
        idle.controller.start()
        idle.controller.onStop()
        idle.controller.onStart()
        assertFalse(idle.adapter.calls.contains("startDiscovery"))
    }

    @Test
    fun `a stale search is refreshed for two seconds before the bond`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.tap("AA")
        assertEquals(listOf("register", "startDiscovery"), rig.adapter.calls)
        rig.now += 1_999
        rig.controller.tick(rig.now)
        assertEquals(listOf("register", "startDiscovery"), rig.adapter.calls)
        rig.now += 2
        rig.controller.tick(rig.now)
        assertEquals(listOf("register", "startDiscovery", "cancelDiscovery", "createBond AA"), rig.adapter.calls)
        assertEquals(1, rig.count("fresh search before bond"))
        assertEquals(1, rig.count("discovery cancelled before bond"))
    }

    @Test
    fun `onStop during the fresh search cancels it and no bond follows`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.tap("AA")
        rig.controller.onStop()
        assertEquals("cancelDiscovery", rig.adapter.calls.last())
        rig.now += 2_001
        rig.controller.tick(rig.now)
        assertFalse(rig.adapter.calls.any { it.startsWith("createBond") })
        assertFalse(rig.listener.events.contains("hold true"))
        rig.controller.onStart()
        assertEquals("startDiscovery", rig.adapter.calls.last())
        rig.now += 2_001
        rig.controller.tick(rig.now)
        assertFalse(rig.adapter.calls.any { it.startsWith("createBond") })
    }

    @Test
    fun `a second tap during the fresh search is ignored`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.onFound("BB", "phone", 0x0200)
        rig.controller.tap("AA")
        rig.controller.tap("BB")
        assertEquals(listOf("register", "startDiscovery"), rig.adapter.calls)
        rig.now += 2_001
        rig.controller.tick(rig.now)
        assertEquals(listOf("register", "startDiscovery", "cancelDiscovery", "createBond AA"), rig.adapter.calls)
    }

    @Test
    fun `the fresh search writes no search end`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.tap("AA")
        rig.now += 1_000
        rig.controller.onDiscoveryFinished()
        assertEquals(0, rig.lines.count { it.startsWith("PairingController: found") })
        assertEquals(0, rig.lines.count { it.startsWith("PairingController: discovery finished") })
        assertTrue(rig.listener.searches.isEmpty())
        rig.now += 1_001
        rig.controller.tick(rig.now)
        assertEquals("createBond AA", rig.adapter.calls.last())
    }

    @Test
    fun `a stale prompt is asked again before the bond`() {
        val rig = Rig(19)
        rig.controller.start()
        rig.controller.onPromptResult(120)
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.now += 181_000
        rig.controller.tap("AA")
        assertTrue(rig.listener.events.contains("prompt"))
        assertFalse(rig.adapter.calls.any { it.startsWith("createBond") })
        assertEquals(1, rig.count("fresh prompt before bond"))
        rig.controller.onPromptResult(120)
        assertTrue(rig.adapter.calls.contains("createBond AA"))
    }

    @Test
    fun `a declined prompt gives no bond`() {
        val rig = Rig(19)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.tap("AA")
        rig.controller.onPromptResult(0)
        assertEquals(1, rig.count("visible request declined"))
        assertFalse(rig.adapter.calls.any { it.startsWith("createBond") })
    }

    @Test
    fun `a refused bond fails the row at once and releases the hold`() {
        val rig = foundAndSearched(25)
        rig.controller.onPromptResult(120)
        rig.adapter.bondOk = false
        rig.controller.tap("AA")
        assertEquals(RowState.FAILED, rig.state("AA"))
        assertEquals(1, rig.count("bond request refused AA"))
        assertEquals(listOf("prereq", "hold true", "hold false"), rig.listener.events.filter { it.startsWith("hold") || it == "prereq" })
    }

    @Test
    fun `a failed search start asks for the prerequisites again`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.adapter.discoveryOk = false
        rig.controller.search()
        assertEquals(1, rig.count("discovery failed"))
        assertEquals(listOf("search failed", "prereq"), rig.listener.events)
    }

    @Test
    fun `a grant after a good search reports no search failure`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.search()
        rig.controller.onDiscoveryFinished()
        rig.controller.onPromptResult(120)
        assertEquals(listOf("prereq"), rig.listener.events)
    }

    @Test
    fun `a search end logs found and finished once`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.search()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.onDiscoveryFinished()
        assertEquals(1, rig.count("found 1"))
        assertEquals(1, rig.count("discovery finished 1"))
        assertEquals(1, rig.count("discovery started"))
    }

    @Test
    fun `the asking hint shows once after twenty seconds`() {
        val rig = foundAndSearched(25)
        rig.controller.onPromptResult(120)
        rig.controller.tap("AA")
        rig.controller.onBondChanged("AA", none, bonding)
        rig.now += 19_999
        rig.controller.tick(rig.now)
        assertEquals(0, rig.count("asking hint shown"))
        rig.now += 2
        rig.controller.tick(rig.now)
        rig.controller.tick(rig.now)
        assertEquals(1, rig.count("asking hint shown"))
    }

    @Test
    fun `a phone found while already bonded gets no row`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.search()
        rig.adapter.bondStates["AA"] = bonded
        rig.controller.onFound("AA", "phone", 0x0200)
        assertTrue(rig.listener.rows.none { it.address == "AA" })
    }

    @Test
    fun `a bond started by the phone adds a row and reaches done`() {
        val rig = Rig(34)
        rig.controller.start()
        rig.controller.onBondChanged("BB", none, bonding)
        rig.controller.onBondChanged("BB", bonding, bonded)
        assertEquals(RowState.DONE, rig.state("BB"))
        assertEquals(0, rig.count("bond requested BB"))
        assertEquals(1, rig.count("bond state DONE BB"))
    }

    @Test
    fun `the hold comes before the bond and goes on done failed and stop`() {
        val done = foundAndSearched(25)
        done.controller.onPromptResult(120)
        done.controller.tap("AA")
        assertTrue(done.listener.events.indexOf("hold true") >= 0)
        done.controller.onBondChanged("AA", none, bonding)
        done.controller.onBondChanged("AA", bonding, bonded)
        done.controller.stop()
        assertEquals(1, done.listener.events.count { it == "hold false" })

        val failed = foundAndSearched(25)
        failed.controller.onPromptResult(120)
        failed.controller.tap("AA")
        failed.controller.onBondChanged("AA", none, bonding)
        failed.controller.onBondChanged("AA", bonding, none)
        assertEquals(1, failed.listener.events.count { it == "hold false" })

        val stopped = foundAndSearched(25)
        stopped.controller.onPromptResult(120)
        stopped.controller.tap("AA")
        stopped.controller.stop()
        assertEquals(1, stopped.listener.events.count { it == "hold false" })
    }

    @Test
    fun `a fresh prompt takes no hold until the grant`() {
        val rig = Rig(19)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.tap("AA")
        assertFalse(rig.listener.events.contains("hold true"))
        rig.controller.onPromptResult(120)
        assertTrue(rig.listener.events.contains("hold true"))
    }

    @Test
    fun `an unavailable prompt still bonds and asks no prompt again`() {
        val rig = Rig(19)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.onFound("BB", "phone", 0x0200)
        rig.controller.tap("AA")
        rig.controller.onPromptUnavailable()
        assertTrue(rig.adapter.calls.contains("createBond AA"))
        rig.controller.onBondChanged("AA", PairingPolicy.BOND_NONE, PairingPolicy.BOND_BONDING)
        rig.controller.onBondChanged("AA", PairingPolicy.BOND_BONDING, PairingPolicy.BOND_NONE)
        rig.controller.tap("BB")
        assertTrue(rig.adapter.calls.contains("createBond BB"))
        assertEquals(1, rig.count("fresh prompt before bond"))
    }

    @Test
    fun `stop restores the prompt route`() {
        val rig = Rig(19)
        rig.controller.start()
        rig.controller.onFound("AA", "phone", 0x0200)
        rig.controller.tap("AA")
        rig.controller.onPromptUnavailable()
        rig.controller.stop()
        rig.controller.start()
        rig.controller.onFound("BB", "phone", 0x0200)
        rig.controller.tap("BB")
        assertEquals(2, rig.count("fresh prompt before bond"))
        assertFalse(rig.adapter.calls.contains("createBond BB"))
    }
}
