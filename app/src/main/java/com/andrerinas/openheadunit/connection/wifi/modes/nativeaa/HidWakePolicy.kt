package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode

/**
 * Whether a Bluetooth HID nudge may wake a phone. Native mode only, a bonded phone from the wake
 * list, no session, no stand-down, and a bounded number of wakes per window.
 */
object HidWakePolicy {

    const val MIN_API = 28

    /** The link stays up this long after the wake reports, so the phone's keyboard comes back. */
    const val DISCONNECT_AFTER_MS = 3_000L

    const val WINDOW_MS = 300_000L
    const val MAX_WAKES_IN_WINDOW = 3

    fun mayWake(
        settingOn: Boolean,
        apiLevel: Int,
        mode: WifiLauncherMode,
        bonded: Boolean,
        inWakeList: Boolean,
        sessionLive: Boolean,
        standDown: Boolean,
        wakesInWindow: Int,
    ): Boolean = settingOn &&
        apiLevel >= MIN_API &&
        mode == WifiLauncherMode.NATIVE &&
        bonded &&
        inWakeList &&
        !sessionLive &&
        !standDown &&
        wakesInWindow < MAX_WAKES_IN_WINDOW

    /** How many of [stamps] fall inside the window that ends at [nowMs]. */
    fun countInWindow(stamps: Collection<Long>, nowMs: Long): Int =
        stamps.count { nowMs - it in 0 until WINDOW_MS }
}
