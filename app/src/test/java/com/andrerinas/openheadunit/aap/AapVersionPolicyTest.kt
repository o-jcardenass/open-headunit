package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AapVersionPolicyTest {

    private fun result(major: Int, minor: Int) =
        AapVersionNegotiation.Result(major, minor, 0, null)

    @Test
    fun `only no compatible version aborts the handshake`() {
        assertTrue(AapVersionPolicy.refusesHandshake(-1))
    }

    @Test
    fun `success and unsolicited both proceed`() {
        assertFalse(AapVersionPolicy.refusesHandshake(0))
        assertFalse(AapVersionPolicy.refusesHandshake(1))
    }

    @Test
    fun `another negative status is not a version refusal`() {
        // A version response that carries an unrelated negative must not cost a working session.
        assertFalse(AapVersionPolicy.refusesHandshake(-8))
        assertFalse(AapVersionPolicy.refusesHandshake(-7))
    }

    @Test
    fun `a phone that selected less than 1 6 is withheld from`() {
        assertTrue(AapVersionPolicy.withholds16(result(1, 2)))
        assertTrue(AapVersionPolicy.withholds16(result(1, 5)))
    }

    @Test
    fun `1 6 and above is sent to`() {
        assertFalse(AapVersionPolicy.withholds16(result(1, 6)))
        assertFalse(AapVersionPolicy.withholds16(result(2, 0)))
    }

    @Test
    fun `an unread version withholds rather than guesses`() {
        assertTrue(AapVersionPolicy.withholds16(null))
    }

    @Test
    fun `what we announce by default is below the gate`() {
        assertTrue(
            AapVersionPolicy.withholds16(
                result(AapVersionNegotiation.ANNOUNCED_MAJOR, AapVersionNegotiation.ANNOUNCED_MINOR)
            )
        )
    }
}
