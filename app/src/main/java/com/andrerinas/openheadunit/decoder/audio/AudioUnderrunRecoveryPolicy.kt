package com.andrerinas.openheadunit.decoder.audio

/**
 * Whether a sink that has just underrun should re-bank its cushion or keep running shallow.
 *
 * A drained track never refills on its own: audio arrives at real time, so the deficit is permanent
 * until something banks again. Re-banking costs a gap of its own, so a link that is simply short is
 * better served running shallow than gapping every few seconds.
 */
object AudioUnderrunRecoveryPolicy {

    /** Minimum spacing between re-banks. */
    const val MIN_SPACING_MS = 2_000L

    /** Above this rate the link is short, not the cushion, and re-banking only adds gaps. */
    const val MAX_REBANKS_PER_MINUTE = 6

    fun shouldRebank(
        underrunsSinceLastCheck: Int,
        sinceLastRebankMs: Long,
        rebanksInLastMinute: Int
    ): Boolean {
        if (underrunsSinceLastCheck <= 0) return false
        if (rebanksInLastMinute >= MAX_REBANKS_PER_MINUTE) return false
        return sinceLastRebankMs >= MIN_SPACING_MS
    }

    /**
     * Re-banks in a minute that say the cushion is too shallow rather than the link too short.
     *
     * Below [MAX_REBANKS_PER_MINUTE] on purpose: a sink gets the chance to climb out before it is
     * left to run shallow, which is the only outcome this policy used to offer.
     */
    const val DEEPEN_AFTER_REBANKS = 3

    /**
     * Whether this sink should bank deeper from here on.
     *
     * Round 2 measured the dial working and the deep end fixing the audible stutter, and a user who
     * is stuttering will not find the setting. Re-banking repeatedly at the depth they picked is the
     * sink saying so itself. [AudioJitterBufferPolicy.deepenedTargetFrames] bounds how far it goes.
     */
    fun shouldDeepen(rebanksInLastMinute: Int): Boolean =
        rebanksInLastMinute >= DEEPEN_AFTER_REBANKS

    /** Whether to say, once, that re-banking has been given up on for this sink. */
    fun hasGivenUp(rebanksInLastMinute: Int): Boolean =
        rebanksInLastMinute >= MAX_REBANKS_PER_MINUTE
}
