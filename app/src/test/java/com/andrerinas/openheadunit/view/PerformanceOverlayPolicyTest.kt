package com.andrerinas.openheadunit.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceOverlayPolicyTest {

    @Test
    fun `app cpu is a share of the whole device, not of one core`() {
        // Two threads busy for a 1000 ms window on a quad core is half the device.
        assertEquals(50, PerformanceOverlayPolicy.appCpuPercent(2000L, 1000L, 4))
        assertEquals(25, PerformanceOverlayPolicy.appCpuPercent(1000L, 1000L, 4))
        assertEquals(100, PerformanceOverlayPolicy.appCpuPercent(4000L, 1000L, 4))
    }

    @Test
    fun `app cpu is clamped when the core count is under-reported`() {
        // A unit that hotplugs cores offline answers fewer than were actually busy.
        assertEquals(100, PerformanceOverlayPolicy.appCpuPercent(2000L, 1000L, 1))
    }

    @Test
    fun `app cpu guards a zero or negative delta`() {
        assertEquals(0, PerformanceOverlayPolicy.appCpuPercent(0L, 0L, 4))
        assertEquals(0, PerformanceOverlayPolicy.appCpuPercent(-500L, 1000L, 4))
        assertEquals(100, PerformanceOverlayPolicy.appCpuPercent(1000L, -1L, 1))
    }

    @Test
    fun `total cpu is the busy share of the aggregate line`() {
        assertEquals(0, PerformanceOverlayPolicy.totalCpuPercent(1000L, 1000L))
        assertEquals(100, PerformanceOverlayPolicy.totalCpuPercent(1000L, 0L))
        assertEquals(40, PerformanceOverlayPolicy.totalCpuPercent(1000L, 600L))
    }

    @Test
    fun `temperature takes the hottest zone and scales milli-degrees`() {
        assertEquals(67, PerformanceOverlayPolicy.temperatureC(listOf(65940, 67210)))
        assertEquals(67, PerformanceOverlayPolicy.temperatureC(listOf(67)))
    }

    @Test
    fun `temperature drops a zone outside the sanity range`() {
        assertEquals(67, PerformanceOverlayPolicy.temperatureC(listOf(67210, 200000, 5)))
        assertNull(PerformanceOverlayPolicy.temperatureC(listOf(200000)))
    }

    @Test
    fun `temperature is null only when no zone answered`() {
        assertNull(PerformanceOverlayPolicy.temperatureC(emptyList()))
        assertNull(PerformanceOverlayPolicy.temperatureC(listOf(null, null)))
    }

    @Test
    fun `an unreadable zone does not discard the zones that answered`() {
        // D-HU's measured shape: 17 zones read, osctsen and outtsen answer EINVAL.
        val zones = List(17) { 60000 + it * 500 } + listOf(null, null)
        assertEquals(68, PerformanceOverlayPolicy.temperatureC(zones))
    }
}
