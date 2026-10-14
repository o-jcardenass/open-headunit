package com.andrerinas.openheadunit.input

import com.andrerinas.openheadunit.aap.protocol.proto.Input.TouchEvent.PointerAction

/**
 * How to end a touch on the phone without a click. Android Auto reads CANCEL (3) and OUTSIDE (4) as
 * a DOWN, so a cancelled gesture is ended by moving every pointer off the canvas and lifting it there.
 *
 * Pure: no Android, no logging.
 */
object TouchReleasePolicy {

    /** Past both sides of every video resolution Android Auto offers (the largest is 3840x2160). */
    const val OFF_CANVAS = 8192

    data class Step(val action: PointerAction, val actionIndex: Int, val pointers: List<Triple<Int, Int, Int>>)

    /** Ends the gesture that holds [pointerIds]: one MOVE off the canvas, then each pointer lifted there. */
    fun release(pointerIds: List<Int>): List<Step> {
        if (pointerIds.isEmpty()) return emptyList()
        val live = pointerIds.toMutableList()
        val steps = mutableListOf(Step(PointerAction.TOUCH_ACTION_MOVE, 0, offCanvas(live)))
        while (live.size > 1) {
            steps += Step(PointerAction.TOUCH_ACTION_POINTER_UP, live.lastIndex, offCanvas(live))
            live.removeAt(live.lastIndex)
        }
        steps += Step(PointerAction.TOUCH_ACTION_UP, 0, offCanvas(live))
        return steps
    }

    /** A press and release that lands on nothing, to put the phone in touch mode. */
    fun tapOffCanvas(): List<Step> = listOf(
        Step(PointerAction.TOUCH_ACTION_DOWN, 0, offCanvas(listOf(0))),
        Step(PointerAction.TOUCH_ACTION_UP, 0, offCanvas(listOf(0)))
    )

    private fun offCanvas(pointerIds: List<Int>) = pointerIds.map { Triple(it, OFF_CANVAS, OFF_CANVAS) }
}
