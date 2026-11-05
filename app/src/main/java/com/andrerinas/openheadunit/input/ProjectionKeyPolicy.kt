package com.andrerinas.openheadunit.input

import android.view.KeyEvent

object ProjectionKeyPolicy {

    fun shouldRouteBackKeyToProjection(actionToPhysicalKeyCode: Map<Int, Int>, keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK &&
                actionToPhysicalKeyCode.containsValue(KeyEvent.KEYCODE_BACK)
    }

    fun isHomeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_HOME
    }

    /** The head unit's firmware must act on this key, so the receiver leaves the broadcast alive. */
    fun firmwareKeepsKey(keyCode: Int?, keyMap: Map<Int, Int>): Boolean {
        return keyCode != null && isLocalKey(keyCode, keyMap, "carkey")
    }

    fun isVolumeKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
    }

    /**
     * An unmapped volume key belongs to the head unit on every route. The mute command is the one
     * deliberate exception, so it keeps a wire code.
     */
    fun isLocalKey(keyCode: Int, keyMap: Map<Int, Int>, source: String): Boolean {
        return isVolumeKey(keyCode) && !keyMap.containsValue(keyCode) && source != "remote-mute"
    }
}
