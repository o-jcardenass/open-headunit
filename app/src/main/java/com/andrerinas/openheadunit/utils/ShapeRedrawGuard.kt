package com.andrerinas.openheadunit.utils

/**
 * The once-per-shape rule for a redraw that survives the reconnect it causes. The redraw's own
 * session keeps [redrawnFor]; any later session starts clean.
 */
data class ShapeRedrawGuard(
    val redrawnFor: ScreenOrientationPolicy.Normalisation? = null,
    private val ownSessionPending: Boolean = false,
) {
    /** A redraw to [shape] was just asked for; the next session is its own. */
    fun afterRedraw(shape: ScreenOrientationPolicy.Normalisation) = ShapeRedrawGuard(shape, true)

    /** A session starts: the redraw's own keeps the shape, every other clears it. */
    fun atSessionStart(): ShapeRedrawGuard =
        if (ownSessionPending) copy(ownSessionPending = false) else ShapeRedrawGuard()
}
