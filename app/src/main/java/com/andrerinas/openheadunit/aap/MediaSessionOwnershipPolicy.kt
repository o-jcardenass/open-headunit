package com.andrerinas.openheadunit.aap

/**
 * Whether this app holds the system media session for a session.
 *
 * Self Mode projects from this device to itself and announces no media sink, so the player and its
 * own session are already here. Ours would only outrank the real one and take the media buttons and
 * the now-playing card with it, and it has no audio of its own to justify either.
 *
 * Pure and unit-tested; the session lives in `AapService`.
 */
object MediaSessionOwnershipPolicy {

    /**
     * @param isLoopbackSession `CommManager.isLoopbackSession` — asked of the session, because a
     *                          launcher flag outlives a launch that never connected.
     */
    fun ownsMediaSession(isLoopbackSession: Boolean): Boolean = !isLoopbackSession
}
