package com.andrerinas.openheadunit.input

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionKeyPolicyTest {

    @Test
    fun `unmapped back key keeps system back behavior`() {
        assertFalse(
            ProjectionKeyPolicy.shouldRouteBackKeyToProjection(
                actionToPhysicalKeyCode = emptyMap(),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `mapped physical back key routes to projection`() {
        assertTrue(
            ProjectionKeyPolicy.shouldRouteBackKeyToProjection(
                actionToPhysicalKeyCode = mapOf(KeyEvent.KEYCODE_BACK to KeyEvent.KEYCODE_BACK),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `physical back can be mapped to another Android Auto action`() {
        assertTrue(
            ProjectionKeyPolicy.shouldRouteBackKeyToProjection(
                actionToPhysicalKeyCode = mapOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS to KeyEvent.KEYCODE_BACK),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `non-back keys do not use the back override`() {
        assertFalse(
            ProjectionKeyPolicy.shouldRouteBackKeyToProjection(
                actionToPhysicalKeyCode = mapOf(KeyEvent.KEYCODE_BACK to KeyEvent.KEYCODE_BACK),
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER
            )
        )
    }

    @Test
    fun `isHomeKey returns true for KEYCODE_HOME and false for other keys`() {
        assertTrue(ProjectionKeyPolicy.isHomeKey(KeyEvent.KEYCODE_HOME))
        assertFalse(ProjectionKeyPolicy.isHomeKey(KeyEvent.KEYCODE_BACK))
        assertFalse(ProjectionKeyPolicy.isHomeKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(ProjectionKeyPolicy.isHomeKey(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    @Test
    fun `unmapped volume keys are local`() {
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)) {
            assertTrue(ProjectionKeyPolicy.isLocalKey(code, emptyMap(), "carkey"))
        }
    }

    @Test
    fun `a mapped volume key is not local`() {
        val keyMap = mapOf(KeyEvent.KEYCODE_DPAD_CENTER to KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse(ProjectionKeyPolicy.isLocalKey(KeyEvent.KEYCODE_VOLUME_UP, keyMap, "carkey"))
        assertTrue(ProjectionKeyPolicy.isLocalKey(KeyEvent.KEYCODE_VOLUME_DOWN, keyMap, "carkey"))
    }

    @Test
    fun `the mute command source is never local`() {
        assertFalse(ProjectionKeyPolicy.isLocalKey(KeyEvent.KEYCODE_VOLUME_MUTE, emptyMap(), "remote-mute"))
        assertTrue(ProjectionKeyPolicy.isLocalKey(KeyEvent.KEYCODE_VOLUME_MUTE, emptyMap(), "remote-keycode"))
    }

    @Test
    fun `mute key 91 back and media keys are not volume keys`() {
        for (code in listOf(KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MEDIA_NEXT)) {
            assertFalse(ProjectionKeyPolicy.isVolumeKey(code))
            assertFalse(ProjectionKeyPolicy.isLocalKey(code, emptyMap(), "carkey"))
        }
    }

    @Test
    fun `an unmapped volume key stays with the firmware`() {
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)) {
            assertTrue(ProjectionKeyPolicy.firmwareKeepsKey(code, emptyMap(), emptySet()))
        }
    }

    @Test
    fun `a mapped volume key is not left to the firmware`() {
        val keyMap = mapOf(KeyEvent.KEYCODE_DPAD_CENTER to KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse(ProjectionKeyPolicy.firmwareKeepsKey(KeyEvent.KEYCODE_VOLUME_UP, keyMap, emptySet()))
    }

    @Test
    fun `an ignored volume key is not left to the firmware`() {
        assertFalse(
            ProjectionKeyPolicy.firmwareKeepsKey(KeyEvent.KEYCODE_VOLUME_UP, emptyMap(), setOf(KeyEvent.KEYCODE_VOLUME_UP))
        )
    }

    @Test
    fun `other keys and no key are not left to the firmware`() {
        assertFalse(ProjectionKeyPolicy.firmwareKeepsKey(KeyEvent.KEYCODE_MEDIA_NEXT, emptyMap(), emptySet()))
        assertFalse(ProjectionKeyPolicy.firmwareKeepsKey(KeyEvent.KEYCODE_MUTE, emptyMap(), emptySet()))
        assertFalse(ProjectionKeyPolicy.firmwareKeepsKey(null, emptyMap(), emptySet()))
    }

    @Test
    fun `physical key routes drop an ignored key`() {
        for (src in listOf("projection", "mediasession", "carkey", "key-broadcast", "remote-keycode")) {
            assertTrue(src, ProjectionKeyPolicy.isIgnored(setOf(87), 87, src))
        }
    }

    @Test
    fun `routes with no wheel press keep an ignored key`() {
        for (src in listOf("media-key-intent", "transport-control", "remote-command", "remote-mute",
            "auto-resume", "assistant", "unknown")) {
            assertFalse(src, ProjectionKeyPolicy.isIgnored(setOf(87), 87, src))
        }
    }

    @Test
    fun `a key outside the set is not ignored`() {
        assertFalse(ProjectionKeyPolicy.isIgnored(setOf(88), 87, "carkey"))
    }
}
