package com.andrerinas.openheadunit.utils

/**
 * Whether a window that hides its status bar also takes `FLAG_FULLSCREEN`. From API 19 the flag
 * keeps a system overlay from pulling the bar down, but a window with it ignores `adjustResize`,
 * so one with a text field goes without. From API 30 the insets controller hides the bars alone.
 */
object FullscreenFlagPolicy {
    private const val KITKAT = 19
    private const val R = 30

    fun adds(sdkInt: Int, resizesForKeyboard: Boolean): Boolean =
        sdkInt < KITKAT || (sdkInt < R && !resizesForKeyboard)
}
