package com.andrerinas.openheadunit.connection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KillOnDisconnectPolicyTest {

    @Test
    fun `an attempt that never reached SSL does not close the app`() {
        assertFalse(KillOnDisconnectPolicy.closesApp(enabled = true, reachedSsl = false, userExit = false))
    }

    @Test
    fun `a session that reached SSL closes the app when it ends`() {
        assertTrue(KillOnDisconnectPolicy.closesApp(enabled = true, reachedSsl = true, userExit = false))
    }

    @Test
    fun `a user exit closes the app even before SSL`() {
        assertTrue(KillOnDisconnectPolicy.closesApp(enabled = true, reachedSsl = false, userExit = true))
    }

    @Test
    fun `the setting off never closes the app`() {
        assertFalse(KillOnDisconnectPolicy.closesApp(enabled = false, reachedSsl = true, userExit = true))
    }
}
