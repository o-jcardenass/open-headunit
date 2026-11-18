package com.andrerinas.openheadunit.input

import android.view.KeyEvent

object ProjectionKeyPolicy {

    /**
     * BACK and the volume keys normally stay with the system, so the user can always leave
     * projection. Once such a key has a binding of its own, the user chose what it does, so it
     * goes there whatever the phone volume switch says.
     */
    fun routesBoundKeyToProjection(bindings: Map<Int, InputAction>, keyCode: Int): Boolean {
        return (keyCode == KeyEvent.KEYCODE_BACK || isVolumeKey(keyCode)) &&
                bindings[keyCode].let { it != null && it != InputAction.None }
    }

    /** Only up and down are volume steps the phone knows; mute stays on this unit. */
    fun sendsVolumeToPhone(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    fun isHomeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_HOME
    }

    fun isVolumeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
    }
}
