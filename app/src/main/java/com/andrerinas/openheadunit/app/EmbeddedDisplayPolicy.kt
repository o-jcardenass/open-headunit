package com.andrerinas.openheadunit.app

/**
 * Whether the projection refuses to open on a display that is not this unit's own. Another app can
 * host an activity on its own virtual display; without the setting that would let it take the picture.
 */
object EmbeddedDisplayPolicy {
    /** The DUDU launcher hosts the projection on its own split display, as it did before the gate. */
    private const val DUDU_LAUNCHER_DISPLAY = "DUDU-launcher-split"

    /**
     * @param trusted FLAG_TRUSTED from API 30; below it false for a private display, else null (unknown).
     * @param foreignOwner another app owns the display; null when unreadable. An unknown owner refuses
     *   unless the display is trusted: below API 30 any app can make a public virtual display.
     */
    fun refuses(
        allowed: Boolean, onDefaultDisplay: Boolean, trusted: Boolean?, foreignOwner: Boolean?,
        displayName: String? = null,
    ): Boolean =
        !allowed && !onDefaultDisplay && displayName?.startsWith(DUDU_LAUNCHER_DISPLAY) != true &&
            (trusted == false || foreignOwner == true || (foreignOwner == null && trusted != true))
}
