package com.andrerinas.openheadunit.input

import com.andrerinas.openheadunit.aap.protocol.proto.Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType
import com.andrerinas.openheadunit.aap.protocol.proto.Input.TouchEvent.PointerAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchReleasePolicyTest {

    private val phoneAccepts = setOf(
        PointerAction.TOUCH_ACTION_DOWN,
        PointerAction.TOUCH_ACTION_UP,
        PointerAction.TOUCH_ACTION_MOVE,
        PointerAction.TOUCH_ACTION_POINTER_DOWN,
        PointerAction.TOUCH_ACTION_POINTER_UP
    )

    @Test
    fun `one pointer moves off the canvas and lifts there`() {
        val steps = TouchReleasePolicy.release(listOf(4))

        assertEquals(listOf(PointerAction.TOUCH_ACTION_MOVE, PointerAction.TOUCH_ACTION_UP), steps.map { it.action })
        steps.forEach { assertEquals(listOf(Triple(4, OFF, OFF)), it.pointers) }
    }

    @Test
    fun `extra pointers lift one at a time before the last`() {
        val steps = TouchReleasePolicy.release(listOf(0, 1, 2))

        assertEquals(
            listOf(
                PointerAction.TOUCH_ACTION_MOVE,
                PointerAction.TOUCH_ACTION_POINTER_UP,
                PointerAction.TOUCH_ACTION_POINTER_UP,
                PointerAction.TOUCH_ACTION_UP
            ),
            steps.map { it.action }
        )
        assertEquals(listOf(listOf(0, 1, 2), listOf(0, 1, 2), listOf(0, 1), listOf(0)), steps.map { s -> s.pointers.map { it.first } })
        assertEquals(listOf(0, 2, 1, 0), steps.map { it.actionIndex })
    }

    @Test
    fun `no pointers sends nothing`() {
        assertTrue(TouchReleasePolicy.release(emptyList()).isEmpty())
    }

    @Test
    fun `tap off the canvas is a press and a release`() {
        val steps = TouchReleasePolicy.tapOffCanvas()

        assertEquals(listOf(PointerAction.TOUCH_ACTION_DOWN, PointerAction.TOUCH_ACTION_UP), steps.map { it.action })
        steps.forEach { assertEquals(listOf(Triple(0, OFF, OFF)), it.pointers) }
    }

    @Test
    fun `only actions the phone accepts go out`() {
        (TouchReleasePolicy.release(listOf(0, 1)) + TouchReleasePolicy.tapOffCanvas())
            .forEach { assertTrue(it.action.name, it.action in phoneAccepts) }
    }

    @Test
    fun `the point is past every video resolution`() {
        VideoCodecResolutionType.values()
            .mapNotNull { Regex("_(\\d+)x(\\d+)").matchEntire(it.name) }
            .forEach { match ->
                val (w, h) = match.destructured
                assertTrue(match.value, OFF > w.toInt() && OFF > h.toInt())
            }
    }

    private companion object {
        const val OFF = TouchReleasePolicy.OFF_CANVAS
    }
}
