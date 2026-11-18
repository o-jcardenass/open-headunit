package com.andrerinas.openheadunit.input

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionKeyPolicyTest {

    @Test
    fun `unmapped back key keeps system back behavior`() {
        assertFalse(
            ProjectionKeyPolicy.routesBoundKeyToProjection(
                bindings = emptyMap(),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `mapped physical back key routes to projection`() {
        assertTrue(
            ProjectionKeyPolicy.routesBoundKeyToProjection(
                bindings = mapOf(KeyEvent.KEYCODE_BACK to InputAction.AaKey(KeyEvent.KEYCODE_BACK)),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `physical back can be mapped to another Android Auto action`() {
        assertTrue(
            ProjectionKeyPolicy.routesBoundKeyToProjection(
                bindings = mapOf(KeyEvent.KEYCODE_BACK to InputAction.AaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `physical back bound to a local action or an app is routed too`() {
        assertTrue(
            ProjectionKeyPolicy.routesBoundKeyToProjection(
                bindings = mapOf(KeyEvent.KEYCODE_BACK to InputAction.Local(LocalAction.DIM_SCREEN)),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `back bound to nothing keeps system back behavior`() {
        assertFalse(
            ProjectionKeyPolicy.routesBoundKeyToProjection(
                bindings = mapOf(KeyEvent.KEYCODE_BACK to InputAction.None),
                keyCode = KeyEvent.KEYCODE_BACK
            )
        )
    }

    @Test
    fun `non-back keys do not use the back override`() {
        assertFalse(
            ProjectionKeyPolicy.routesBoundKeyToProjection(
                bindings = mapOf(KeyEvent.KEYCODE_BACK to InputAction.AaKey(KeyEvent.KEYCODE_BACK)),
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
    fun `isVolumeKey covers up, down and mute only`() {
        assertTrue(ProjectionKeyPolicy.isVolumeKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(ProjectionKeyPolicy.isVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertTrue(ProjectionKeyPolicy.isVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(ProjectionKeyPolicy.isVolumeKey(KeyEvent.KEYCODE_MUTE))
        assertFalse(ProjectionKeyPolicy.isVolumeKey(KeyEvent.KEYCODE_BACK))
        assertFalse(ProjectionKeyPolicy.isVolumeKey(KeyEvent.KEYCODE_MEDIA_NEXT))
    }

    @Test
    fun `a bound volume key routes to the projection, an unbound one does not`() {
        val bound = mapOf(KeyEvent.KEYCODE_VOLUME_UP to InputAction.AaKey(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertTrue(ProjectionKeyPolicy.routesBoundKeyToProjection(bound, KeyEvent.KEYCODE_VOLUME_UP))
        assertFalse(ProjectionKeyPolicy.routesBoundKeyToProjection(bound, KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(ProjectionKeyPolicy.routesBoundKeyToProjection(emptyMap(), KeyEvent.KEYCODE_VOLUME_UP))
        val none = mapOf(KeyEvent.KEYCODE_VOLUME_UP to InputAction.None)
        assertFalse(ProjectionKeyPolicy.routesBoundKeyToProjection(none, KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test
    fun `only volume up and down go to the phone, mute stays on this unit`() {
        assertTrue(ProjectionKeyPolicy.sendsVolumeToPhone(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(ProjectionKeyPolicy.sendsVolumeToPhone(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertFalse(ProjectionKeyPolicy.sendsVolumeToPhone(KeyEvent.KEYCODE_VOLUME_MUTE))
        assertFalse(ProjectionKeyPolicy.sendsVolumeToPhone(KeyEvent.KEYCODE_BACK))
    }
}
