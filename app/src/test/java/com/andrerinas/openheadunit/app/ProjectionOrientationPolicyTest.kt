package com.andrerinas.openheadunit.app

import android.content.pm.ActivityInfo
import com.andrerinas.openheadunit.utils.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectionOrientationPolicyTest {

    private fun pin(
        setting: Settings.ScreenOrientation,
        locked: Boolean = true,
        landscape: Boolean = true,
    ) = ProjectionOrientationPolicy.pinnedOrientation(setting, locked, landscape)

    private fun requested(
        setting: Settings.ScreenOrientation,
        locked: Boolean = true,
        landscape: Boolean = true,
    ) = ProjectionOrientationPolicy.orientationFor(setting, locked, landscape)

    @Test
    fun `a locked session pins AUTO to the negotiated orientation`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, pin(Settings.ScreenOrientation.AUTO))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            pin(Settings.ScreenOrientation.AUTO, landscape = false)
        )
    }

    @Test
    fun `a locked session pins SYSTEM too, which is the default setting`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, pin(Settings.ScreenOrientation.SYSTEM))
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            pin(Settings.ScreenOrientation.SYSTEM, landscape = false)
        )
    }

    @Test
    fun `nothing is pinned before the resolution is locked`() {
        for (setting in Settings.ScreenOrientation.values()) {
            assertNull(setting.name, pin(setting, locked = false))
        }
    }

    @Test
    fun `a fixed setting is never pinned, whatever was negotiated`() {
        val fixed = listOf(
            Settings.ScreenOrientation.LANDSCAPE,
            Settings.ScreenOrientation.LANDSCAPE_REVERSE,
            Settings.ScreenOrientation.PORTRAIT,
            Settings.ScreenOrientation.PORTRAIT_REVERSE,
        )
        for (setting in fixed) {
            assertNull(setting.name, pin(setting))
            assertNull(setting.name, pin(setting, landscape = false))
        }
    }

    @Test
    fun `unlocked AUTO lets the sensor choose the orientation to negotiate in`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            requested(Settings.ScreenOrientation.AUTO, locked = false)
        )
    }

    @Test
    fun `unlocked SYSTEM keeps the user's own rotation setting`() {
        assertEquals(
            Settings.ScreenOrientation.SYSTEM.androidOrientation,
            requested(Settings.ScreenOrientation.SYSTEM, locked = false)
        )
    }

    @Test
    fun `a fixed setting is requested as itself in both lock states`() {
        for (locked in listOf(true, false)) {
            assertEquals(
                Settings.ScreenOrientation.PORTRAIT_REVERSE.androidOrientation,
                requested(Settings.ScreenOrientation.PORTRAIT_REVERSE, locked = locked)
            )
        }
    }

    @Test
    fun `the requested orientation is the pin whenever there is one`() {
        for (setting in listOf(Settings.ScreenOrientation.AUTO, Settings.ScreenOrientation.SYSTEM)) {
            for (landscape in listOf(true, false)) {
                assertEquals(
                    "$setting landscape=$landscape",
                    pin(setting, landscape = landscape),
                    requested(setting, landscape = landscape)
                )
            }
        }
    }
}
