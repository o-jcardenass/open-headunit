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

    @Test
    fun `an unarmed mode refuses before anything else is asked`() {
        assertEquals(
            GeometryProbePolicy.FireVerdict.MODE_OFF,
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.OFF, false, null, false)
        )
    }

    @Test
    fun `a rotation before the transport is up names the transport`() {
        assertEquals(
            GeometryProbePolicy.FireVerdict.NO_TRANSPORT,
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.SERVICE_REDISCOVERY, false, null, false)
        )
    }

    @Test
    fun `the first rotation of a session fires`() {
        for (mode in allModes.filter { it != GeometryProbePolicy.OFF }) {
            assertEquals(
                mode.toString(),
                GeometryProbePolicy.FireVerdict.FIRE,
                GeometryProbePolicy.fireVerdict(mode, true, null, false)
            )
        }
    }

    @Test
    fun `one rotation fires one lever`() {
        assertEquals(
            GeometryProbePolicy.FireVerdict.ALREADY_FIRED,
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.CONFIG_READY, true, false, false)
        )
    }

    @Test
    fun `turning back fires again`() {
        assertEquals(
            GeometryProbePolicy.FireVerdict.FIRE,
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.CONFIG_READY, true, false, true)
        )
    }

    @Test
    fun `every refusal has a name of its own`() {
        val verdicts = listOf(
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.OFF, true, null, true),
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.UI_THEME, false, null, true),
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.UI_THEME, true, true, true),
            GeometryProbePolicy.fireVerdict(GeometryProbePolicy.UI_THEME, true, true, false),
        )
        assertEquals(verdicts.size, verdicts.toSet().size)
    }
}
