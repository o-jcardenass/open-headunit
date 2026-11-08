package com.andrerinas.openheadunit.decoder.audio

/**
 * Maps speed to a volume level of 0 to 4 and says when to move one step.
 * Hysteresis and a hold stop a speed near a band edge from making the volume jump.
 */
object SpeedVolumePolicy {

    val EDGES_KMH = listOf(40, 70, 100, 120)
    const val MARGIN_KMH = 3
    const val HOLD_MS = 10_000L

    enum class Step { UP, DOWN, NONE }

    /** [level] is null until the first speed; [lastStepMs] is null until the first step. */
    data class State(val level: Int? = null, val lastStepMs: Long? = null)

    data class Result(val state: State, val step: Step)

    fun levelFor(speedKmh: Float): Int = EDGES_KMH.count { it <= speedKmh }

    fun isValid(speedKmh: Float?): Boolean = speedKmh != null && !speedKmh.isNaN() && speedKmh >= 0f

    fun next(state: State, speedKmh: Float?, nowMs: Long): Result {
        if (speedKmh == null || !isValid(speedKmh)) return Result(state, Step.NONE)
        val level = state.level ?: return Result(State(levelFor(speedKmh), null), Step.NONE)
        val step = when {
            level < EDGES_KMH.size && speedKmh > EDGES_KMH[level] + MARGIN_KMH -> Step.UP
            level > 0 && speedKmh < EDGES_KMH[level - 1] - MARGIN_KMH -> Step.DOWN
            else -> Step.NONE
        }
        if (step == Step.NONE) return Result(state, Step.NONE)
        val last = state.lastStepMs
        if (last != null && nowMs - last < HOLD_MS) return Result(state, Step.NONE)
        val moved = if (step == Step.UP) level + 1 else level - 1
        return Result(State(moved, nowMs), step)
    }

    /** Signed steps from the stored level to the level for this speed, applied at once on resume. */
    fun resume(heldLevel: Int, speedKmh: Float): Int = levelFor(speedKmh) - heldLevel

    /** A step moves the volume only toward a policy level that differs from the level the index holds. */
    fun moves(step: Step, policyLevelAfter: Int, heldLevel: Int): Boolean = when (step) {
        Step.UP -> policyLevelAfter > heldLevel
        Step.DOWN -> policyLevelAfter < heldLevel
        Step.NONE -> false
    }
}
