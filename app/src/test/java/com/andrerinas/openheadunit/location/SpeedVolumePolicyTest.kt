package com.andrerinas.openheadunit.location

import com.andrerinas.openheadunit.location.SpeedVolumePolicy.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedVolumePolicyTest {

    @Test
    fun `off adds nothing and takes back whatever was added`() {
        assertEquals(0, SpeedVolumePolicy.targetSteps(Mode.OFF, speedKmh = 120f, current = 0))
        assertEquals(0, SpeedVolumePolicy.targetSteps(Mode.OFF, speedKmh = 120f, current = 3))
    }

    @Test
    fun `rising speed takes each step at its threshold`() {
        assertEquals(0, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 29.9f, current = 0))
        assertEquals(1, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 30f, current = 0))
        assertEquals(2, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 61f, current = 1))
        assertEquals(2, SpeedVolumePolicy.targetSteps(Mode.HIGH, speedKmh = 45f, current = 0))
    }

    @Test
    fun `steps are capped at the mode's maximum`() {
        assertEquals(3, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 200f, current = 0))
        assertEquals(5, SpeedVolumePolicy.targetSteps(Mode.HIGH, speedKmh = 200f, current = 0))
    }

    @Test
    fun `falling speed keeps a step until the hysteresis is cleared`() {
        // Threshold 30, hysteresis 5: the step stays down to 25 and goes below it.
        assertEquals(1, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 27f, current = 1))
        assertEquals(1, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 25.1f, current = 1))
        assertEquals(0, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 24.9f, current = 1))
    }

    @Test
    fun `speed wandering around a threshold does not pump the volume`() {
        var steps = 0
        for (speed in listOf(29f, 31f, 28f, 32f, 27f, 30f, 26f)) {
            steps = SpeedVolumePolicy.targetSteps(Mode.LOW, speed, steps)
        }
        assertEquals(1, steps)
    }

    @Test
    fun `stopping drops every added step`() {
        assertEquals(0, SpeedVolumePolicy.targetSteps(Mode.HIGH, speedKmh = 0f, current = 5))
    }

    @Test
    fun `turning the mode down mid-drive returns the surplus`() {
        assertEquals(3, SpeedVolumePolicy.targetSteps(Mode.LOW, speedKmh = 120f, current = 5))
    }

    @Test
    fun `stored values round-trip and unknown ones fall back to off`() {
        Mode.values().forEach { assertEquals(it, Mode.fromInt(it.value)) }
        assertEquals(Mode.OFF, Mode.fromInt(99))
        assertEquals(Mode.OFF, Mode.fromInt(-1))
    }

    @Test
    fun `placement keeps the pinned target and stream while steps are non-zero`() {
        val pinned = SpeedVolumePolicy.Placement(onPhone = true, stream = 3)
        assertEquals(pinned, SpeedVolumePolicy.placement(2, pinned, phoneNow = false, streamNow = 5))
    }

    @Test
    fun `placement reads the setting again at zero steps or with nothing pinned`() {
        val pinned = SpeedVolumePolicy.Placement(onPhone = true, stream = 3)
        val now = SpeedVolumePolicy.Placement(onPhone = false, stream = 5)
        assertEquals(now, SpeedVolumePolicy.placement(0, pinned, phoneNow = false, streamNow = 5))
        assertEquals(now, SpeedVolumePolicy.placement(2, null, phoneNow = false, streamNow = 5))
    }

    @Test
    fun `phone steps carry to the same phone only`() {
        val onA = SpeedVolumePolicy.Placement(onPhone = true, stream = 3, phone = "Google Pixel 7")
        assertEquals(SpeedVolumePolicy.Carry.KEEP, SpeedVolumePolicy.carry(4, onA, "Google Pixel 7", toPhoneNow = true))
        assertEquals(SpeedVolumePolicy.Carry.DROP, SpeedVolumePolicy.carry(4, onA, "motorola edge", toPhoneNow = true))
        assertEquals(SpeedVolumePolicy.Carry.WAIT, SpeedVolumePolicy.carry(4, onA, null, toPhoneNow = true))
    }

    @Test
    fun `a first phone step waits for the phone's name, so it never records a null phone`() {
        assertEquals(SpeedVolumePolicy.Carry.WAIT, SpeedVolumePolicy.carry(0, null, null, toPhoneNow = true))
        val stale = SpeedVolumePolicy.Placement(onPhone = false, stream = 3)
        assertEquals(SpeedVolumePolicy.Carry.WAIT, SpeedVolumePolicy.carry(0, stale, null, toPhoneNow = true))
        assertEquals(SpeedVolumePolicy.Carry.KEEP, SpeedVolumePolicy.carry(0, null, "Google Pixel 7", toPhoneNow = true))
    }

    @Test
    fun `head unit steps never wait or drop`() {
        val onA = SpeedVolumePolicy.Placement(onPhone = true, stream = 3, phone = "Google Pixel 7")
        val onUnit = SpeedVolumePolicy.Placement(onPhone = false, stream = 3)
        assertEquals(SpeedVolumePolicy.Carry.KEEP, SpeedVolumePolicy.carry(0, onA, null, toPhoneNow = false))
        assertEquals(SpeedVolumePolicy.Carry.KEEP, SpeedVolumePolicy.carry(2, onUnit, null, toPhoneNow = true))
    }

    @Test
    fun `a new placement records the phone it steps on`() {
        assertEquals("Google Pixel 7",
            SpeedVolumePolicy.placement(0, null, phoneNow = true, streamNow = 3, phone = "Google Pixel 7").phone)
    }
}
