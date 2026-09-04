package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.zbt

import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.zbt.ZbtAttemptPolicy.Presence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZbtAttemptPolicyTest {

    private fun bytes(hex: String): ByteArray =
        hex.trim().split(Regex("\\s+")).map { it.toInt(16).toByte() }.toByteArray()

    private fun presence(msgId: Int, hex: String) =
        ZbtAttemptPolicy.readPresence(msgId, ZbtMessages.parse(bytes(hex)))

    // ---------------------------------------------------------------------------------------------
    // Reading the module's link state — against the bodies it actually sent
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `LinkInfo reports a connected Android Auto phone`() {
        // The captured pair from 2026-08-12, 130 ms apart, differing only in is_connect. If field 2
        // and field 3 were ever read the wrong way round, a disconnected phone of the right type
        // and a connected phone of the wrong type would swap places — and both are states this
        // module really produces.
        val connected = presence(
            ZbtMessages.LINK_INFO,
            "08 84 02 10 01 18 01 20 00 2a 0c 30 30 30 32 35 42 44 33 35 30 42 43"
        )!!
        assertTrue(connected.connected)
        assertTrue(connected.androidAuto)
        assertTrue(connected.usable)

        val gone = presence(
            ZbtMessages.LINK_INFO,
            "08 84 02 10 01 18 00 20 00 2a 0c 30 30 30 32 35 42 44 33 35 30 42 43"
        )!!
        assertFalse(gone.connected)
        assertTrue("still an Android Auto phone, just not connected", gone.androidAuto)
        assertFalse(gone.usable)
    }

    @Test
    fun `LinkInfo2 also carries the phone's name and address`() {
        // The one message that names the phone. Our own Bluetooth stack cannot see this device at
        // all, so this is the only place a log line can say which phone it is.
        val p = presence(
            ZbtMessages.LINK_INFO2,
            "08 8c 02 10 01 18 01 20 00 2a 0c 30 30 30 32 35 42 44 33 35 30 42 43 32 07 43 41 52 " +
                "38 30 33 32 3a 0c 43 30 35 37 32 34 37 36 44 37 34 46 42 11 48 4f 4e 4f 52 20 4d " +
                "61 67 69 63 38 20 4c 69 74 65"
        )!!
        assertTrue(p.usable)
        assertEquals("HONOR Magic8 Lite", p.phoneName)
        assertEquals("C0572476D74F", p.phoneMac)
    }

    @Test
    fun `a phone the module does not class as Android Auto is not worth attempting`() {
        // phone_type=3 — seen live on this hardware when the reporter cycled Bluetooth.
        val p = presence(ZbtMessages.LINK_INFO, "08 84 02 10 03 18 01")!!
        assertTrue(p.connected)
        assertFalse(p.androidAuto)
        assertFalse(p.usable)
    }

    @Test
    fun `messages that say nothing about the phone say nothing`() {
        assertNull(presence(ZbtMessages.INIT_INFO, "08 82 02"))
        assertNull(presence(ZbtMessages.RFCOMM_DATA, "00 01 02"))
        assertNull(ZbtAttemptPolicy.readPresence(ZbtMessages.LINK_INFO, null))
    }

    // ---------------------------------------------------------------------------------------------
    // Arrival is the edge, not the level
    // ---------------------------------------------------------------------------------------------

    private val here = Presence(connected = true, androidAuto = true)
    private val away = Presence(connected = false, androidAuto = true)

    @Test
    fun `a phone appearing is an arrival`() {
        assertTrue(ZbtAttemptPolicy.isArrival(null, here))
        assertTrue(ZbtAttemptPolicy.isArrival(away, here))
    }

    @Test
    fun `the module's heartbeat is not an arrival`() {
        // It re-sends link state about every twelve seconds. Treating each one as news would start
        // an attempt every twelve seconds for as long as the phone sat there connected.
        assertFalse(ZbtAttemptPolicy.isArrival(here, here))
    }

    @Test
    fun `a phone leaving is not an arrival`() {
        assertFalse(ZbtAttemptPolicy.isArrival(here, away))
        assertFalse(ZbtAttemptPolicy.isArrival(away, away))
    }

    // ---------------------------------------------------------------------------------------------
    // Whether to attempt
    // ---------------------------------------------------------------------------------------------

    private fun shouldAttempt(
        phonePresent: Boolean = true,
        attemptInFlight: Boolean = false,
        sessionConnected: Boolean = false,
        sinceLastAttemptMs: Long? = null,
        minIntervalMs: Long = ZbtAttemptPolicy.MIN_ATTEMPT_INTERVAL_MS
    ) = ZbtAttemptPolicy.shouldAttempt(
        phonePresent, attemptInFlight, sessionConnected, sinceLastAttemptMs, minIntervalMs
    )

    @Test
    fun `a present phone and nothing in the way is an attempt`() {
        assertTrue(shouldAttempt())
    }

    @Test
    fun `no attempt without a phone, alongside one already running, or once a session is up`() {
        assertFalse(shouldAttempt(phonePresent = false))
        assertFalse(shouldAttempt(attemptInFlight = true))
        assertFalse(shouldAttempt(sessionConnected = true))
    }

    @Test
    fun `attempts are spaced`() {
        assertFalse(shouldAttempt(sinceLastAttemptMs = 0L))
        assertFalse(shouldAttempt(sinceLastAttemptMs = ZbtAttemptPolicy.MIN_ATTEMPT_INTERVAL_MS - 1))
        assertTrue(shouldAttempt(sinceLastAttemptMs = ZbtAttemptPolicy.MIN_ATTEMPT_INTERVAL_MS))
    }

    @Test
    fun `a widened interval holds an attempt the ordinary one would have allowed`() {
        val widened = ZbtAttemptPolicy.MIN_ATTEMPT_INTERVAL_MS * 4
        assertTrue(shouldAttempt(sinceLastAttemptMs = ZbtAttemptPolicy.MIN_ATTEMPT_INTERVAL_MS))
        assertFalse(
            shouldAttempt(
                sinceLastAttemptMs = ZbtAttemptPolicy.MIN_ATTEMPT_INTERVAL_MS,
                minIntervalMs = widened
            )
        )
        assertTrue(shouldAttempt(sinceLastAttemptMs = widened, minIntervalMs = widened))
    }

    @Test
    fun `a widened interval does not revive an attempt something else refused`() {
        assertFalse(shouldAttempt(phonePresent = false, minIntervalMs = 1L))
        assertFalse(shouldAttempt(sessionConnected = true, minIntervalMs = 1L))
    }
}
