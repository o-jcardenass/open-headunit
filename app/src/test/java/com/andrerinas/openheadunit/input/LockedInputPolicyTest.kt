package com.andrerinas.openheadunit.input

import android.view.KeyEvent
import com.andrerinas.openheadunit.main.FavoriteTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedInputPolicyTest {

    @Test
    fun `setting off never holds input, locked or not`() {
        assertFalse(LockedInputPolicy.shouldEngage(enabled = false, deviceLocked = true, screenOff = true))
        assertFalse(LockedInputPolicy.shouldEngage(enabled = false, deviceLocked = null, screenOff = false))
    }

    @Test
    fun `setting on holds input while the unit needs its PIN`() {
        assertTrue(LockedInputPolicy.shouldEngage(enabled = true, deviceLocked = true, screenOff = false))
    }

    @Test
    fun `setting on releases input once the unit is unlocked`() {
        assertFalse(LockedInputPolicy.shouldEngage(enabled = true, deviceLocked = false, screenOff = false))
    }

    @Test
    fun `an unknown lock state counts as locked`() {
        assertTrue(LockedInputPolicy.shouldEngage(enabled = true, deviceLocked = null, screenOff = false))
    }

    @Test
    fun `screen off holds input even before the unit reports locked`() {
        assertTrue(LockedInputPolicy.shouldEngage(enabled = true, deviceLocked = false, screenOff = true))
    }

    @Test
    fun `the lock state is only needed with the setting on and the screen lit`() {
        assertTrue(LockedInputPolicy.needsLockState(enabled = true, screenOff = false))
        assertFalse(LockedInputPolicy.needsLockState(enabled = true, screenOff = true))
        assertFalse(LockedInputPolicy.needsLockState(enabled = false, screenOff = false))
        assertFalse(LockedInputPolicy.needsLockState(enabled = false, screenOff = true))
    }

    @Test
    fun `where the lock state is not needed it does not change the hold`() {
        for (enabled in listOf(true, false)) for (screenOff in listOf(true, false)) {
            if (LockedInputPolicy.needsLockState(enabled, screenOff)) continue
            val answers = listOf(true, false, null).map { LockedInputPolicy.shouldEngage(enabled, it, screenOff) }
            assertEquals("enabled=$enabled screenOff=$screenOff", 1, answers.toSet().size)
        }
    }

    @Test
    fun `not engaged lets every event through`() {
        assertTrue(LockedInputPolicy.allows(engaged = false, keycode = null))
        assertTrue(LockedInputPolicy.allows(engaged = false, keycode = KeyEvent.KEYCODE_SEARCH))
    }

    @Test
    fun `engaged drops touch, scroll and every key but the map key`() {
        assertFalse(LockedInputPolicy.allows(engaged = true, keycode = null))
        listOf(
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT,
            65536, 65537
        ).forEach { assertFalse("keycode $it", LockedInputPolicy.allows(engaged = true, keycode = it)) }
        assertTrue(LockedInputPolicy.allows(engaged = true, keycode = LockedInputPolicy.KEYCODE_AA_NAVIGATION))
    }

    @Test
    fun `engaged holds app launches from bound keys and keeps screen actions`() {
        val launch = InputAction.LaunchApp(FavoriteTarget.App("com.example"))
        assertFalse(LockedInputPolicy.allowsBoundAction(engaged = true, action = launch))
        assertTrue(LockedInputPolicy.allowsBoundAction(engaged = false, action = launch))
        LocalAction.values().forEach {
            assertTrue("$it", LockedInputPolicy.allowsBoundAction(engaged = true, action = InputAction.Local(it)))
        }
    }

    @Test
    fun `volume keys pass while engaged and other keys still drop`() {
        assertTrue(LockedInputPolicy.allows(engaged = true, keycode = KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(LockedInputPolicy.allows(engaged = true, keycode = KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(LockedInputPolicy.allows(engaged = true, keycode = KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(LockedInputPolicy.allows(engaged = true, keycode = null))
    }

    @Test
    fun `the option is supported from API 26`() {
        assertFalse(LockedInputPolicy.supported(25))
        assertTrue(LockedInputPolicy.supported(26))
    }

    @Test
    fun `a new owner adopts a hold the old screen set, and the old release keeps it`() {
        val old = Any()
        val new = Any()
        try {
            assertTrue(LockedInputPolicy.update(old, true, claim = true))
            assertFalse(LockedInputPolicy.update(new, true, claim = true))
            assertFalse(LockedInputPolicy.release(old))
            assertTrue(LockedInputPolicy.engaged)
            assertTrue(LockedInputPolicy.release(new))
            assertFalse(LockedInputPolicy.engaged)
        } finally {
            LockedInputPolicy.release(new)
        }
    }

    @Test
    fun `a replaced screen's late screen-off cannot take the hold back and clear it`() {
        val old = Any()
        val new = Any()
        try {
            LockedInputPolicy.update(old, false, claim = true)
            assertFalse(LockedInputPolicy.update(new, false, claim = true))
            assertFalse(LockedInputPolicy.update(old, true))
            assertFalse(LockedInputPolicy.engaged)
            assertTrue(LockedInputPolicy.update(new, true))
            assertFalse(LockedInputPolicy.update(old, false))
            assertFalse(LockedInputPolicy.release(old))
            assertTrue(LockedInputPolicy.engaged)
        } finally {
            LockedInputPolicy.release(new)
        }
    }

    @Test
    fun `the sole owner's release clears the hold, a second release changes nothing`() {
        val a = Any()
        assertTrue(LockedInputPolicy.update(a, true, claim = true))
        assertTrue(LockedInputPolicy.release(a))
        assertFalse(LockedInputPolicy.engaged)
        assertFalse(LockedInputPolicy.release(a))
    }

    @Test
    fun `the map key goes out with a running transport and is owed without one`() {
        assertEquals(LockedInputPolicy.MapKey.SEND, LockedInputPolicy.mapKey(transportStarted = true))
        assertEquals(LockedInputPolicy.MapKey.OWE, LockedInputPolicy.mapKey(transportStarted = false))
    }
}
