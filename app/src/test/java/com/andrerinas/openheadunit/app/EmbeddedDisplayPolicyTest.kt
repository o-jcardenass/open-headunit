package com.andrerinas.openheadunit.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedDisplayPolicyTest {

    @Test
    fun `the default display always shows`() {
        for (allowed in listOf(false, true)) for (trusted in listOf(false, true, null)) for (foreign in listOf(false, true, null)) {
            assertFalse(EmbeddedDisplayPolicy.refuses(allowed, onDefaultDisplay = true, trusted = trusted, foreignOwner = foreign))
        }
    }

    @Test
    fun `another display shows when the setting allows it`() {
        for (trusted in listOf(false, true, null)) for (foreign in listOf(false, true, null)) {
            assertFalse(EmbeddedDisplayPolicy.refuses(allowed = true, onDefaultDisplay = false, trusted = trusted, foreignOwner = foreign))
        }
    }

    @Test
    fun `an untrusted display is refused whoever owns it`() {
        for (foreign in listOf(false, true, null)) {
            assertTrue(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = false, foreignOwner = foreign))
        }
    }

    @Test
    fun `a display another app owns is refused even when trusted`() {
        assertTrue(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = true, foreignOwner = true))
    }

    @Test
    fun `api 26 to 29 a public virtual display of another app is refused`() {
        // No FLAG_PRIVATE below API 30 reads as unknown trust, not as trusted.
        assertTrue(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = null, foreignOwner = true))
    }

    @Test
    fun `api 26 to 29 a public display with an unreadable owner is refused`() {
        assertTrue(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = null, foreignOwner = null))
    }

    @Test
    fun `api 26 to 29 a system display with no app owner shows`() {
        assertFalse(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = null, foreignOwner = false))
    }

    @Test
    fun `a trusted display with no app owner, or an unreadable one from api 30, shows`() {
        assertFalse(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = true, foreignOwner = false))
        assertFalse(EmbeddedDisplayPolicy.refuses(allowed = false, onDefaultDisplay = false, trusted = true, foreignOwner = null))
    }

    @Test
    fun `the DUDU launcher display is exempt, any other name is not`() {
        assertFalse(EmbeddedDisplayPolicy.refuses(false, false, false, true, "DUDU-launcher-split"))
        assertFalse(EmbeddedDisplayPolicy.refuses(false, false, null, true, "DUDU-launcher-split-2"))
        assertTrue(EmbeddedDisplayPolicy.refuses(false, false, null, true, "Overlay #1"))
        assertTrue(EmbeddedDisplayPolicy.refuses(false, false, null, true, null))
    }
}
