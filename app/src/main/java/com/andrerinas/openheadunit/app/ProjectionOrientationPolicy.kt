package com.andrerinas.openheadunit.app

import android.content.pm.ActivityInfo
import com.andrerinas.openheadunit.utils.Settings

/**
 * Which way up the projection is allowed to sit. Video geometry is negotiated once, at service
 * discovery, and no AAP message can move it, so a session that follows the device into a new
 * orientation is scaled into a shape the phone was never told about.
 */
object ProjectionOrientationPolicy {

    /**
     * The orientation a live session is pinned to, or null when nothing is pinned. SYSTEM is here
     * as well as AUTO: it is the default setting, so leaving it out left the default install the
     * only one that could rotate mid-session (issue #996).
     */
    fun pinnedOrientation(
        setting: Settings.ScreenOrientation,
        resolutionLocked: Boolean,
        negotiatedLandscape: Boolean,
        renegotiationProbe: Boolean = false,
    ): Int? {
        // A probe exists to let the panel rotate under a live session, so it lifts the pin.
        if (renegotiationProbe) return null
        if (!resolutionLocked) return null
        if (setting != Settings.ScreenOrientation.AUTO && setting != Settings.ScreenOrientation.SYSTEM) {
            return null
        }
        return if (negotiatedLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /** The orientation to request outright: the pin when there is one, the setting otherwise. */
    fun orientationFor(
        setting: Settings.ScreenOrientation,
        resolutionLocked: Boolean,
        negotiatedLandscape: Boolean,
        renegotiationProbe: Boolean = false,
    ): Int = pinnedOrientation(setting, resolutionLocked, negotiatedLandscape, renegotiationProbe)
        ?: if (setting == Settings.ScreenOrientation.AUTO) {
            // Unlocked AUTO lets the sensor choose which orientation the session is negotiated in.
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        } else {
            setting.androidOrientation
        }
}
