package com.andrerinas.openheadunit.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SettingsBackupManagerTest {
    private val prefs = mapOf<String, Any?>(
        "dpi-panel-values" to "2376x968=175",
        "dpi-panel-first" to "2376x968",
        "dpi-panels-active" to true
    )

    @Test
    fun `export carries the panel map and leaves the latch out`() {
        val settings = org.json.JSONObject(SettingsBackupManager.exportToJson(prefs, "1", 1)).getJSONObject("settings")
        assertEquals("2376x968=175", settings.getString("dpi-panel-values"))
        assertFalse(settings.has("dpi-panel-first"))
        assertFalse(settings.has("dpi-panels-active"))
    }

    @Test
    fun `import skips the latch keys`() {
        val json = """{"format":"${SettingsBackupManager.FORMAT}","settings":{"dpi-panel-values":"2376x968=175","dpi-panel-first":"1x1","dpi-panels-active":true}}"""
        val data = SettingsBackupManager.parseImportJson(json)
        assertEquals(setOf("dpi-panel-values"), data.values.keys)
    }

    @Test
    fun `a changed panel map needs a projection restart`() {
        assertTrue(SettingsBackupManager.requiresProjectionRestart(setOf("dpi-panel-values")))
    }
}
