package com.andrerinas.openheadunit.decoder.audio

import com.andrerinas.openheadunit.decoder.audio.SpeedVolumePolicy.State
import com.andrerinas.openheadunit.decoder.audio.SpeedVolumePolicy.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedVolumePolicyTest {

    private fun at(level: Int?, last: Long? = null) = State(level, last)

    @Test
    fun `a rise from 20 to 80 steps up once, waits out the hold, then steps again`() {
        var r = SpeedVolumePolicy.next(State(), 20f, 0)
        assertEquals(Step.NONE, r.step)
        r = SpeedVolumePolicy.next(r.state, 80f, 0)
        assertEquals(Step.UP, r.step)
        r = SpeedVolumePolicy.next(r.state, 80f, 5_000)
        assertEquals(Step.NONE, r.step)
        r = SpeedVolumePolicy.next(r.state, 80f, 10_000)
        assertEquals(Step.UP, r.step)
        assertEquals(2, r.state.level)
    }

    @Test
    fun `a speed inside the margin does not step`() {
        assertEquals(Step.NONE, SpeedVolumePolicy.next(at(1), 71f, 0).step)
        assertEquals(Step.UP, SpeedVolumePolicy.next(at(1), 74f, 0).step)
        assertEquals(Step.NONE, SpeedVolumePolicy.next(at(2), 69f, 0).step)
        assertEquals(Step.DOWN, SpeedVolumePolicy.next(at(2), 66f, 0).step)
    }

    @Test
    fun `a speed that swings across 70 for a minute gives no step`() {
        var state = at(2)
        var steps = 0
        for (i in 0 until 30) {
            val r = SpeedVolumePolicy.next(state, if (i % 2 == 0) 69f else 71f, i * 2_000L)
            state = r.state
            if (r.step != Step.NONE) steps++
        }
        assertEquals(0, steps)
    }

    @Test
    fun `two crossings 9 seconds apart give one step and 11 seconds apart give two`() {
        val first = SpeedVolumePolicy.next(at(0), 50f, 0)
        assertEquals(Step.UP, first.step)
        assertEquals(Step.NONE, SpeedVolumePolicy.next(first.state, 80f, 9_000).step)
        assertEquals(Step.UP, SpeedVolumePolicy.next(first.state, 80f, 11_000).step)
    }

    @Test
    fun `no usable speed gives no step and keeps the state`() {
        val state = at(2, 500)
        for (speed in listOf(null, -1f, Float.NaN)) {
            repeat(100) {
                val r = SpeedVolumePolicy.next(state, speed, it * 2_000L)
                assertEquals(Step.NONE, r.step)
                assertEquals(state, r.state)
            }
        }
    }

    @Test
    fun `the first speed sets the base without a step`() {
        val r = SpeedVolumePolicy.next(State(), 110f, 0)
        assertEquals(Step.NONE, r.step)
        assertEquals(3, r.state.level)
        var state = r.state
        repeat(30) {
            val n = SpeedVolumePolicy.next(state, 110f, (it + 1) * 2_000L)
            assertEquals(Step.NONE, n.step)
            state = n.state
        }
    }

    @Test
    fun `levels follow the band edges`() {
        val expected = mapOf(0f to 0, 40f to 1, 69f to 1, 70f to 2, 100f to 3, 119f to 3, 120f to 4, 200f to 4)
        for ((speed, level) in expected) assertEquals("speed $speed", level, SpeedVolumePolicy.levelFor(speed))
        assertEquals(0, SpeedVolumePolicy.levelFor(39f))
    }

    @Test
    fun `resume is the signed distance to the level for the speed`() {
        assertEquals(-2, SpeedVolumePolicy.resume(2, 0f))
        assertEquals(0, SpeedVolumePolicy.resume(2, 80f))
        assertEquals(2, SpeedVolumePolicy.resume(2, 130f))
    }

    @Test
    fun `a step moves only toward a level that differs from the held one`() {
        assertTrue(SpeedVolumePolicy.moves(Step.UP, 1, 0))
        assertFalse(SpeedVolumePolicy.moves(Step.UP, 1, 1))
        assertFalse(SpeedVolumePolicy.moves(Step.DOWN, 0, 0))
        assertTrue(SpeedVolumePolicy.moves(Step.DOWN, 0, 1))
        assertFalse(SpeedVolumePolicy.moves(Step.NONE, 0, 1))
    }
}
