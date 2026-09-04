package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.ExternalBtTransportPolicy.Route
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole truth table, because four call sites read this decision and the only thing keeping them
 * from disagreeing is that they all read it from here.
 */
class ExternalBtTransportPolicyTest {

    private val evidence = "/dev/rf_serial exists"

    private fun route(
        externalBtEvidence: String? = null,
        zbt: Boolean = false,
        ignore: Boolean = false
    ) = ExternalBtTransportPolicy.route(externalBtEvidence, zbt, ignore)

    @Test
    fun `a unit with no external-BT markers is normal, whatever the settings say`() {
        // Both settings are only ever offered on detected units, but nothing stops one surviving in
        // prefs after a restore or a firmware change. Neither must divert a working unit.
        assertEquals(Route.NORMAL, route())
        assertEquals(Route.NORMAL, route(zbt = true))
        assertEquals(Route.NORMAL, route(ignore = true))
        assertEquals(Route.NORMAL, route(zbt = true, ignore = true))
    }

    @Test
    fun `external Bluetooth with both switches off is refused, as it is today`() {
        assertEquals(Route.BLOCKED, route(evidence))
    }

    @Test
    fun `the opt-in takes the module route`() {
        assertEquals(Route.ZBT, route(evidence, zbt = true))
    }

    @Test
    fun `detection alone never takes the module route`() {
        // ExternalBtPolicy identifies a class of hardware; some of that class reaches its module
        // over Binder and has nothing on the daemon's port at all. Routing on detection would send
        // those units down a transport that cannot exist for them.
        assertEquals(Route.BLOCKED, route(evidence, zbt = false))
    }

    @Test
    fun `the compatibility override still forces this unit's own radio`() {
        // nativeAaIgnoreExternalBt is a shipped, translated setting. The module transport must add
        // a route, not remove the one users already have.
        assertEquals(Route.NORMAL, route(evidence, ignore = true))
    }

    @Test
    fun `the module route wins when both escapes are on`() {
        // They answer the same detection differently: one uses a radio the phone is not bonded to,
        // the other the chip it is. Only the second can carry bytes.
        assertEquals(Route.ZBT, route(evidence, zbt = true, ignore = true))
    }

    @Test
    fun `with the module transport off, nothing about the old behaviour changed`() {
        // The regression fence. On every unit that has never opted in, this must reduce exactly to
        // what externalBtOverridden decided before the module route existed.
        assertEquals(Route.NORMAL, route(null, zbt = false, ignore = false))
        assertEquals(Route.NORMAL, route(null, zbt = false, ignore = true))
        assertEquals(Route.BLOCKED, route(evidence, zbt = false, ignore = false))
        assertEquals(Route.NORMAL, route(evidence, zbt = false, ignore = true))
    }
}
