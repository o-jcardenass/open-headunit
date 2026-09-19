package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.direct.GroupIdentityStability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WppTcpServePolicyTest {

    @Test
    fun `an advertised endpoint is one we serve`() {
        assertTrue(WppTcpServePolicy.servesDial(WppEndpointDecision.Advertise(5299)))
    }

    @Test
    fun `a withheld endpoint is one we refuse`() {
        assertFalse(WppTcpServePolicy.servesDial(WppEndpointDecision.Withhold("because")))
    }

    /**
     * The property that matters: serving and advertising are the same decision. A unit that is not
     * safe to be remembered by is not safe to answer, because answering hands out the same name.
     */
    @Test
    fun `serving is the exact complement of withholding, over every real input`() {
        for (strategy in NativeStrategy.values()) {
            for (identity in GroupIdentityStability.values()) {
                for (port in listOf(null, 5299)) {
                    val decision = WppEndpointPolicy.decide(strategy, port, identity)
                    assertEquals(
                        "$strategy/$identity/port=$port",
                        decision is WppEndpointDecision.Advertise,
                        WppTcpServePolicy.servesDial(decision)
                    )
                }
            }
        }
    }

    @Test
    fun `the unit that measured the wedge is refused, and says why`() {
        // D-SAM: below API 29, so the platform names the group and renames it every create.
        val decision = WppEndpointPolicy.decide(
            NativeStrategy.WIFI_DIRECT, 5299, GroupIdentityStability.RENAMED
        )
        assertFalse(WppTcpServePolicy.servesDial(decision))
        assertTrue(WppTcpServePolicy.refusalReason(decision).contains("forget this head unit on the phone"))
    }

    @Test
    fun `our own access point is served, whatever the group verdict says`() {
        for (identity in GroupIdentityStability.values()) {
            val decision = WppEndpointPolicy.decide(NativeStrategy.HOTSPOT, 5299, identity)
            assertTrue(identity.name, WppTcpServePolicy.servesDial(decision))
        }
    }

    @Test
    fun `every refusal is told to the phone while Bluetooth can take it instead`() {
        for (identity in GroupIdentityStability.values()) {
            for (port in listOf(null, 5299)) {
                val decision = WppEndpointPolicy.decide(NativeStrategy.WIFI_DIRECT, port, identity)
                if (WppTcpServePolicy.servesDial(decision)) continue
                assertTrue(
                    "$identity port=$port",
                    WppTcpServePolicy.rejectsDial(decision, canRunRfcomm = true)
                )
            }
        }
    }

    @Test
    fun `a refusal is swallowed when there is no Bluetooth route to fall back to`() {
        // Withdrawing the endpoint from a phone whose only way back is closed strands it: the
        // stall it has is at least a stall it can retry out of.
        val decision = WppEndpointPolicy.decide(
            NativeStrategy.WIFI_DIRECT, 5299, GroupIdentityStability.CHANGED
        )
        assertFalse(WppTcpServePolicy.rejectsDial(decision, canRunRfcomm = false))
    }

    @Test
    fun `a dial we serve is never rejected, whatever the Bluetooth listeners are doing`() {
        val decision = WppEndpointPolicy.decide(
            NativeStrategy.HOTSPOT, 5299, GroupIdentityStability.NOT_MEASURED
        )
        assertTrue(WppTcpServePolicy.servesDial(decision))
        assertFalse(WppTcpServePolicy.rejectsDial(decision, canRunRfcomm = true))
        assertFalse(WppTcpServePolicy.rejectsDial(decision, canRunRfcomm = false))
    }

    @Test
    fun `a server that is not listening is not served either`() {
        val decision = WppEndpointPolicy.decide(NativeStrategy.HOTSPOT, null, GroupIdentityStability.STABLE)
        assertFalse(WppTcpServePolicy.servesDial(decision))
    }
}
