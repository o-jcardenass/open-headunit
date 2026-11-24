package com.andrerinas.openheadunit.utils

import android.app.Application
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The device-storage copy of "is USB chosen" that the attach screen reads before unlock. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SettingsUsbSelectedMirrorTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun `with nothing stored USB counts as chosen`() {
        assertTrue(Settings.isUsbSelected(context))
    }

    @Test fun `choosing Wi-Fi only clears the mirror and adding USB sets it`() {
        val settings = Settings(context)
        settings.connectionModes = setOf(Settings.ConnectionMode.WIFI)
        assertFalse(Settings.isUsbSelected(context))
        settings.connectionModes = setOf(Settings.ConnectionMode.WIFI, Settings.ConnectionMode.USB)
        assertTrue(Settings.isUsbSelected(context))
    }

    @Test fun `an empty selection sets the mirror`() {
        val settings = Settings(context)
        settings.connectionModes = setOf(Settings.ConnectionMode.WIFI)
        settings.connectionModes = emptySet()
        assertTrue(Settings.isUsbSelected(context))
    }

    @Test fun `reset sets the mirror`() {
        val settings = Settings(context)
        settings.connectionModes = setOf(Settings.ConnectionMode.WIFI)
        settings.reset()
        assertTrue(Settings.isUsbSelected(context))
    }
}
