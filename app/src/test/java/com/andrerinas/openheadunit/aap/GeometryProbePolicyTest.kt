package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryProbePolicyTest {

    private val configArms = listOf(
        GeometryProbePolicy.CONFIG_READY,
        GeometryProbePolicy.CONFIG_WAIT_THEN_READY,
        GeometryProbePolicy.CONFIG_THEN_FOCUS_CYCLE,
    )

    private val allModes = listOf(
        GeometryProbePolicy.OFF,
        GeometryProbePolicy.SERVICE_REDISCOVERY,
        GeometryProbePolicy.CONFIG_READY,
        GeometryProbePolicy.CONFIG_WAIT_THEN_READY,
        GeometryProbePolicy.CONFIG_THEN_FOCUS_CYCLE,
        GeometryProbePolicy.UI_THEME,
    )

    @Test
    fun `off is the shipped mode and does nothing at all`() {
        assertEquals(0, GeometryProbePolicy.OFF)
        assertFalse(GeometryProbePolicy.isActive(GeometryProbePolicy.OFF))
        assertFalse(GeometryProbePolicy.announcesSecondConfig(GeometryProbePolicy.OFF))
        assertFalse(GeometryProbePolicy.liftsOrientationPin(GeometryProbePolicy.OFF))
        assertFalse(GeometryProbePolicy.renegotiates(GeometryProbePolicy.OFF))
    }

    @Test
    fun `an unknown mode is treated as active rather than silently ignored`() {
        assertTrue(GeometryProbePolicy.isActive(99))
    }

    @Test
    fun `only the Config arms announce something to select between`() {
        for (mode in allModes) {
            assertEquals(mode.toString(), mode in configArms, GeometryProbePolicy.announcesSecondConfig(mode))
        }
    }

    @Test
    fun `every active mode lifts the pin, because a probe needs the panel to turn`() {
        for (mode in allModes) {
            assertEquals(
                mode.toString(),
                GeometryProbePolicy.isActive(mode),
                GeometryProbePolicy.liftsOrientationPin(mode)
            )
        }
    }

    @Test
    fun `the ui_theme arm lifts the pin but does not re-derive the geometry`() {
        assertTrue(GeometryProbePolicy.liftsOrientationPin(GeometryProbePolicy.UI_THEME))
        assertFalse(GeometryProbePolicy.renegotiates(GeometryProbePolicy.UI_THEME))
    }

    @Test
    fun `every geometry arm re-derives`() {
        for (mode in configArms + GeometryProbePolicy.SERVICE_REDISCOVERY) {
            assertTrue(mode.toString(), GeometryProbePolicy.renegotiates(mode))
        }
    }
}
