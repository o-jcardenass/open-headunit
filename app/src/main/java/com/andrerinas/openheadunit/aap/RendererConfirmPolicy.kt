package com.andrerinas.openheadunit.aap

/**
 * How long the renderer question stays up. Only time the user could see it counts, so a new
 * renderer that has drawn nothing yet, or a bar nobody saw, is never taken for an answer.
 */
object RendererConfirmPolicy {

    const val SHOW_MS = 10_000L
    const val TICK_MS = 1_000L

    /** [onScreen] is a started screen: a paused one still shows in picture in picture or split screen. */
    fun seen(
        onScreen: Boolean,
        frameRendered: Boolean,
        noticeUp: Boolean,
        nightOverlayUp: Boolean,
    ): Boolean = onScreen && frameRendered && !noticeUp && !nightOverlayUp

    /** What is left of [SHOW_MS] after one [TICK_MS]. 0 closes the bar as answered. */
    fun remainingMs(remainingMs: Long, seen: Boolean): Long =
        if (seen) (remainingMs - TICK_MS).coerceAtLeast(0L) else remainingMs
}
