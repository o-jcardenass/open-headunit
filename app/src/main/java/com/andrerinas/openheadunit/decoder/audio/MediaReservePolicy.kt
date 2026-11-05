package com.andrerinas.openheadunit.decoder.audio

/**
 * Media-only reserve learned from a starvation and held through repeating link stalls,
 * because the normal target forgets a stall before the next scan. A bank grows only while it refills.
 * A stall over [MAX_MS] teaches nothing, since no reserve could carry it.
 */
internal class MediaReservePolicy(private val sampleRate: Int) {
    var heldFrames = 0
        private set
    private var starvedAtMs = -1L
    private var targetAtStarvation = 0
    private var learnUntilMs = -1L
    private var lastStallMs = -1L
    private var lastReleaseMs = -1L

    fun onStarved(nowMs: Long, targetFrames: Int) {
        learnUntilMs = nowMs + LEARN_MS
        lastStallMs = nowMs
        if (starvedAtMs < 0) { starvedAtMs = nowMs; targetAtStarvation = targetFrames }
    }

    /** The first arrival after a starvation sizes the stall before the bank resumes. */
    fun onArrival(nowMs: Long) {
        if (starvedAtMs < 0) return
        raise(targetAtStarvation + frames((nowMs - starvedAtMs).coerceAtLeast(0)) + frames(PAD_MS))
        starvedAtMs = -1L
    }

    /** [supplyPeakFrames] is the lag the last catch-up repaid. */
    fun onSupplyPeak(nowMs: Long, supplyPeakFrames: Int) {
        if (nowMs <= learnUntilMs) raise(supplyPeakFrames + frames(PAD_MS))
        if (heldFrames == 0) return
        if (supplyPeakFrames >= frames(STALL_MS)) lastStallMs = nowMs
        if (nowMs - lastStallMs >= HOLD_MS && nowMs - lastReleaseMs >= 1000) {
            heldFrames = (heldFrames - frames(RELEASE_MS_PER_S)).coerceAtLeast(0)
            lastReleaseMs = nowMs
        }
    }

    /** Silence proves no clean link and is no part of a stall, so both clocks move forward. */
    fun onIdle(idleMs: Long) {
        if (lastStallMs >= 0) lastStallMs += idleMs
        if (starvedAtMs >= 0) starvedAtMs += idleMs
    }

    fun reset() {
        heldFrames = 0; starvedAtMs = -1L; learnUntilMs = -1L; lastStallMs = -1L; lastReleaseMs = -1L
    }

    private fun raise(needFrames: Int) {
        if (needFrames > frames(MAX_MS)) return
        heldFrames = maxOf(heldFrames, needFrames)
    }

    private fun frames(ms: Long) = (sampleRate * ms / 1000).toInt()

    companion object {
        const val MAX_MS = 1200L
        const val PAD_MS = 100L
        const val HOLD_MS = 200_000L
        const val STALL_MS = 200L
        const val LEARN_MS = 10_000L
        const val RELEASE_MS_PER_S = 5L
    }
}
