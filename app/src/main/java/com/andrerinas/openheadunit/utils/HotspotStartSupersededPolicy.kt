package com.andrerinas.openheadunit.utils

/**
 * A start that a stop has passed would still bring the access point up and hold the radio.
 * This decides what a start, a restart and a teardown do when the stop generation has moved.
 */
object HotspotStartSupersededPolicy {
    enum class Step { CARRY_ON, ABANDON, TAKE_DOWN }

    const val START_DRAIN_CEILING_MS = 1_500L

    fun beforeRequest(startedAt: Int, current: Int): Step =
        if (current != startedAt) Step.ABANDON else Step.CARRY_ON

    fun keepsWaiting(startedAt: Int, current: Int): Boolean = current == startedAt

    fun afterWait(startedAt: Int, current: Int, apUp: Boolean, attempted: Boolean): Step = when {
        current == startedAt -> Step.CARRY_ON
        apUp || attempted -> Step.TAKE_DOWN
        else -> Step.ABANDON
    }

    fun restartReenables(ownStop: Int, current: Int): Boolean = current == ownStop

    fun drainsStart(startInFlight: Boolean, waitedMs: Long): Boolean =
        startInFlight && waitedMs < START_DRAIN_CEILING_MS
}
