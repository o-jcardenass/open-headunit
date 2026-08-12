package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.ExternalBtTransportPolicy.Route
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole truth table, because four call sites read this decision and the only thing keeping them
 * from disagreeing is that they all read it from here.
 */
class ExternalBtTransportPolicyTest {

    private val evidence = "/dev/rf_serial exists"

    private fun route(externalBtEvidence: String? = null, zbt: Boolean = false) =
        ExternalBtTransportPolicy.route(externalBtEvidence, zbt)

    @Test
    fun `a unit with no external-BT markers is normal, whatever the setting says`() {
        // The setting is only ever offered on detected units, but nothing stops it surviving in
        // prefs after a restore or a firmware change. It must not divert a working unit.
        assertEquals(Route.NORMAL, route())
        assertEquals(Route.NORMAL, route(zbt = true))
    }

    @Test
    fun `external Bluetooth with the transport off is refused, as it is today`() {
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
    fun `there is no longer any way to force android bluetooth on a module unit`() {
        // A manually named secondary Bluetooth service used to route these units to NORMAL. It let
        // the app *attempt* a connection over a radio the phone is not bonded to, which produced
        // the original failure — writes that succeed and nothing on the air — and looked like a
        // setting worth trying. Only two outcomes remain, and both are honest.
        assertEquals(Route.BLOCKED, route(evidence, zbt = false))
        assertEquals(Route.ZBT, route(evidence, zbt = true))
    }
}
