package com.andrerinas.openheadunit.decoder.video

/**
 * Which slot of one output batch may go to the surface. Three or more ready frames are a backlog,
 * and showing each one leaves the picture behind audio, so only the newest is sure to show.
 * Under the timing controller a slot keeps its turn while its deadline is in the future.
 */
internal object VideoCatchUpPolicy {
    fun offers(
        readyCount: Int,
        index: Int,
        timingControlsPresentation: Boolean,
        deadline: Long,
        repairEligible: Boolean,
        sessionHasRendered: Boolean,
    ): Boolean {
        if (readyCount <= 2 || index == readyCount - 1) return true
        if (!timingControlsPresentation) return false
        // Deadline 0 is due or late. A repair picture and the first picture still show.
        return deadline != 0L || repairEligible || !sessionHasRendered
    }
}
