package com.andrerinas.openheadunit.decoder.audio

/** A station scan on a single-radio unit stalls the link ~1.5 s, past the jitter ceiling, and repeats.
 * Hold the longest recent stall plus a margin; after ten quiet seconds release 5 ms per second. */
internal class StallReservePolicy {
    var reserveMs = 0L
        private set
    private var releaseFromMs = -1L
    private var stallAtMs = -1L
    private var latestNeedMs = 0L

    fun onSupplyGap(nowMs: Long, gapMs: Long) {
        release(nowMs)
        if (gapMs < MIN_STALL_MS) return
        val needMs = (gapMs + MARGIN_MS).coerceAtMost(MAX_RESERVE_MS)
        reserveMs = maxOf(reserveMs, needMs)
        latestNeedMs = needMs
        releaseFromMs = nowMs + HOLD_MS
        stallAtMs = nowMs
    }

    /** A held media reserve shows the stalls repeat slower than [HOLD_MS], so the latest stall's
     * depth keeps the media hold. The depth above it releases as usual, so one deep stall does not pin the delay. */
    fun release(nowMs: Long, mediaHeld: Boolean = false) {
        if (reserveMs == 0L) return
        val frozen = mediaHeld && nowMs - stallAtMs < MediaReservePolicy.HOLD_MS
        if (frozen && reserveMs <= latestNeedMs) releaseFromMs = maxOf(releaseFromMs, nowMs)
        if (nowMs < releaseFromMs) return
        val steps = (nowMs - releaseFromMs) / 1000
        if (steps == 0L) return
        reserveMs = (reserveMs - steps * 5).coerceAtLeast(if (frozen) latestNeedMs else 0)
        releaseFromMs += steps * 1000
    }

    internal companion object {
        // Above AudioJitterBufferPolicy.MAX_TARGET_MS, which the jitter policy already covers.
        const val MIN_STALL_MS = 500L
        const val MARGIN_MS = 300L
        // Leaves a third of the 3 s bank for the catch-up burst.
        const val MAX_RESERVE_MS = 2000L
        const val HOLD_MS = 10_000L
    }
}
