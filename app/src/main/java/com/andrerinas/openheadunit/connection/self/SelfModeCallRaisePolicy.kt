package com.andrerinas.openheadunit.connection.self

/**
 * Whether to put the projection back over a call screen: three raises a call in Self Mode, one in
 * the first 10 s over a head unit dialer. The budget is per call, so a chosen call screen stays.
 */
object SelfModeCallRaisePolicy {

    /** How long to let the call screen settle before the first attempt. */
    const val FIRST_ATTEMPT_DELAY_MS = 600L

    /** Spacing between attempts while the call is up. */
    const val RETRY_INTERVAL_MS = 1_200L

    /** Attempts allowed per call. Once spent, a switch to the Dialer by hand sticks. */
    const val MAX_ATTEMPTS_PER_CALL = 3

    /** Wait after the call ends before the final attempt. Android usually shows the projection again by itself. */
    const val POST_CALL_SETTLE_MS = 1_000L

    /**
     * How long an episode waits for the audio mode to report a call. The call screen covers us
     * seconds before the mode reports one; no call in this window means the cover was not a call.
     */
    const val CALL_CONFIRM_WINDOW_MS = 5_000L

    /** Raises allowed per call over a head unit call screen, which may be a camera. */
    const val MAX_ATTEMPTS_HEAD_UNIT = 1

    /** A raise this long after the call start could land on a camera, so none is made. */
    const val HEAD_UNIT_RAISE_WINDOW_MS = 10_000L

    /** A no-call reading this close before a call reading shows that OHU saw the call start. */
    const val START_EVIDENCE_MS = 2_000L

    /** A ledger unread this long may have missed the end of a call, so it is dropped. */
    const val LEDGER_STALE_MS = 60_000L

    /** How long a ringing phone holds an episode open for an answer. */
    const val RING_WAIT_MS = 60_000L

    /** Tick spacing while attempts remain. */
    const val TICK_MS = 400L

    /** Tick spacing once the attempts are spent and the only thing left to notice is the call ending. */
    const val IDLE_TICK_MS = 2_000L

    /** Who owns the call screen that covers us, which sets the budget. */
    enum class Scope(val label: String, val maxAttempts: Int) {
        SELF_MODE("Self Mode", MAX_ATTEMPTS_PER_CALL),
        HEAD_UNIT("head unit", MAX_ATTEMPTS_HEAD_UNIT),
    }

    enum class Action {
        /** Nothing to do yet. Keep ticking. */
        WAIT,

        /** Put the projection back on top. */
        RAISE,

        /** We are back, or there is nothing here to fix. Stop ticking. */
        DONE,
    }

    /** Whether a cover is one to watch for a call, and if not, why not. */
    enum class CoverVerdict(val reason: String) {
        /** Nothing in the way. */
        OPEN("watching for a call"),

        /** Home or Recents. Someone who chose to leave is not argued with. */
        USER_LEFT("the user left deliberately"),

        /** Outside Self Mode the head unit's own dialer wins unless its switch is on. */
        NOT_SELF_MODE("not Self Mode, and the head unit call raise is off"),

        PIP("picture-in-picture owns the screen"),

        DISABLED("the setting is off"),

        /** The episode already running keeps its own clock and budget. */
        ALREADY_OPEN("already watching this cover"),
    }

    /** Whether this cover is one to watch. The audio mode is not checked: the cover comes before the call. */
    fun coverVerdict(
        userLeft: Boolean,
        selfMode: Boolean,
        pipActive: Boolean,
        enabled: Boolean,
        headUnitEnabled: Boolean,
        episodeOpen: Boolean,
    ): CoverVerdict = when {
        episodeOpen -> CoverVerdict.ALREADY_OPEN
        userLeft -> CoverVerdict.USER_LEFT
        !selfMode && !headUnitEnabled -> CoverVerdict.NOT_SELF_MODE
        pipActive -> CoverVerdict.PIP
        selfMode && !enabled -> CoverVerdict.DISABLED
        else -> CoverVerdict.OPEN
    }

    /** What the caller knows about the current call across episodes. Times are 0 when unknown. */
    data class CallLedger(
        val callSinceMs: Long = 0L,
        val startSeen: Boolean = false,
        val attempts: Int = 0,
        val lastRaiseAtMs: Long = 0L,
        val lastReadingAtMs: Long = 0L,
        val lastNoCallAtMs: Long = 0L,
        val signalSinceMs: Long = 0L,
        val signalStartSeen: Boolean = false,
    )

    /** Folds one audio mode reading, a mode change callback or a poll, into [ledger]. */
    fun recordCall(
        ledger: CallLedger,
        nowMs: Long,
        callActive: Boolean,
        ringing: Boolean,
        fromModeChange: Boolean,
    ): CallLedger {
        val l = if (ledger.lastReadingAtMs > 0L && nowMs - ledger.lastReadingAtMs > LEDGER_STALE_MS) {
            CallLedger()
        } else {
            ledger
        }
        val evidence = l.lastNoCallAtMs > 0L && nowMs - l.lastNoCallAtMs <= START_EVIDENCE_MS
        val signalSince = if (l.signalSinceMs == 0L) nowMs else l.signalSinceMs
        // A first ring reading with no quiet reading just before it may come long after the real ring.
        // A cover reading can come before the mode change callback for the same ring, so that callback confirms it.
        val signalConfirmed = fromModeChange && ringing && nowMs - l.signalSinceMs <= START_EVIDENCE_MS
        val signalSeen = if (l.signalSinceMs == 0L) fromModeChange || evidence else l.signalStartSeen || signalConfirmed
        if (!callActive) {
            return CallLedger(
                lastReadingAtMs = nowMs,
                lastNoCallAtMs = nowMs,
                signalSinceMs = if (ringing) signalSince else 0L,
                signalStartSeen = ringing && signalSeen,
            )
        }
        if (l.callSinceMs == 0L) {
            return l.copy(
                callSinceMs = nowMs,
                startSeen = fromModeChange || evidence,
                attempts = 0,
                lastRaiseAtMs = 0L,
                lastReadingAtMs = nowMs,
                signalSinceMs = signalSince,
                signalStartSeen = signalSeen,
            )
        }
        val confirmed = fromModeChange && nowMs - l.callSinceMs <= START_EVIDENCE_MS
        return l.copy(startSeen = l.startSeen || confirmed, lastReadingAtMs = nowMs)
    }

    /** The ledger after a raise at [nowMs]. A raise after the call ended spends nothing. */
    fun recordRaise(ledger: CallLedger, nowMs: Long, callActive: Boolean): CallLedger = ledger.copy(
        attempts = if (callActive) ledger.attempts + 1 else ledger.attempts,
        lastRaiseAtMs = nowMs,
    )

    /** What the caller carries between ticks. A mode change can open it after [coveredSinceMs]. */
    data class Episode(
        val startedAtMs: Long,
        val coveredSinceMs: Long = startedAtMs,
        val scope: Scope = Scope.SELF_MODE,
        val sawCallActive: Boolean = false,
        val attempts: Int = 0,
        val lastAttemptAtMs: Long = 0L,
        val callEndedAtMs: Long = 0L,
        val postCallAttemptUsed: Boolean = false,
    )

    /** Folds this tick's observation of the call into [episode]. Call before [decide]. */
    fun observe(episode: Episode, nowMs: Long, callActive: Boolean): Episode = when {
        callActive -> episode.copy(sawCallActive = true, callEndedAtMs = 0L)
        episode.sawCallActive && episode.callEndedAtMs == 0L -> episode.copy(callEndedAtMs = nowMs)
        else -> episode
    }

    private class Decision(val action: Action, val reason: String)

    /**
     * The next action. [nowMs] is `elapsedRealtime()`; [callStarting] (ringing) holds an episode open.
     * [reverseGear] is the seam for a real gear signal: no known unit sets it during a raise.
     */
    fun decide(
        nowMs: Long,
        episode: Episode,
        callActive: Boolean,
        callStarting: Boolean,
        isForeground: Boolean,
        pipActive: Boolean,
        scope: Scope,
        ledger: CallLedger,
        reverseGear: Boolean,
    ): Action = rule(nowMs, episode, callActive, callStarting, isForeground, pipActive, scope, ledger, reverseGear).action

    /** Why a decision came out the way it did, for the log. */
    fun describe(
        nowMs: Long,
        episode: Episode,
        callActive: Boolean,
        callStarting: Boolean,
        isForeground: Boolean,
        pipActive: Boolean,
        scope: Scope,
        ledger: CallLedger,
        reverseGear: Boolean,
    ): String = rule(nowMs, episode, callActive, callStarting, isForeground, pipActive, scope, ledger, reverseGear).reason

    private fun rule(
        nowMs: Long,
        episode: Episode,
        callActive: Boolean,
        callStarting: Boolean,
        isForeground: Boolean,
        pipActive: Boolean,
        scope: Scope,
        ledger: CallLedger,
        reverseGear: Boolean,
    ): Decision {
        if (isForeground) return Decision(Action.DONE, "the projection is back in front")
        if (pipActive) return Decision(Action.DONE, "picture-in-picture owns the screen")

        val headUnit = scope == Scope.HEAD_UNIT
        if (headUnit && ledger.signalSinceMs > 0L) {
            if (ledger.signalSinceMs - episode.coveredSinceMs > CALL_CONFIRM_WINDOW_MS) {
                return Decision(Action.DONE, "the cover came before the call, leaving it alone")
            }
            // A cover well before the answer is the ring's dialer only if it came at a ring start OHU saw.
            if (ledger.callSinceMs > 0L && ledger.callSinceMs - episode.coveredSinceMs > CALL_CONFIRM_WINDOW_MS) {
                if (!ledger.signalStartSeen) {
                    return Decision(Action.DONE, "the ring start was not seen and the cover came well before the answer, leaving it alone")
                }
                if (episode.coveredSinceMs - ledger.signalSinceMs > CALL_CONFIRM_WINDOW_MS) {
                    return Decision(Action.DONE, "the cover came during the ring, well before the answer, leaving it alone")
                }
            }
        }

        if (callActive) {
            if (headUnit) {
                if (!ledger.startSeen) {
                    return Decision(Action.DONE, "the call start was not seen, leaving the call screen alone")
                }
                if (nowMs - ledger.callSinceMs > HEAD_UNIT_RAISE_WINDOW_MS) {
                    return Decision(Action.DONE, "the call started over 10 s ago, leaving the call screen alone")
                }
                if (episode.attempts >= scope.maxAttempts) {
                    return Decision(Action.DONE, "the one raise for this call is spent")
                }
            } else if (episode.attempts >= scope.maxAttempts) {
                return Decision(Action.WAIT, "attempts spent, leaving the call screen alone")
            }
            if (reverseGear) return Decision(Action.WAIT, "reverse gear is on, leaving the call screen alone")
            val dueAtMs = when {
                episode.attempts > 0 -> episode.lastAttemptAtMs + RETRY_INTERVAL_MS
                headUnit -> maxOf(episode.startedAtMs, ledger.callSinceMs) + FIRST_ATTEMPT_DELAY_MS
                else -> episode.startedAtMs + FIRST_ATTEMPT_DELAY_MS
            }
            return if (nowMs >= dueAtMs) {
                Decision(Action.RAISE, "attempt ${episode.attempts + 1} of ${scope.maxAttempts} during the call")
            } else {
                Decision(Action.WAIT, "waiting for the next attempt")
            }
        }

        if (!episode.sawCallActive) {
            val waitedMs = nowMs - episode.startedAtMs
            if (callStarting && waitedMs < RING_WAIT_MS) return Decision(Action.WAIT, "the phone is ringing")
            return if (waitedMs >= CALL_CONFIRM_WINDOW_MS) {
                Decision(Action.DONE, "whatever covered the projection was not a call")
            } else {
                Decision(Action.WAIT, "no call yet")
            }
        }

        if (headUnit) return Decision(Action.DONE, "the call ended, leaving the call screen alone")
        if (episode.postCallAttemptUsed) return Decision(Action.DONE, "nothing left to do")
        val endedAtMs = if (episode.callEndedAtMs > 0L) episode.callEndedAtMs else nowMs
        return if (nowMs - endedAtMs >= POST_CALL_SETTLE_MS) {
            Decision(Action.RAISE, "the call ended and the projection is still covered")
        } else {
            Decision(Action.WAIT, "waiting for the call screen to close on its own")
        }
    }

    /** The episode to carry forward after an attempt at [nowMs]. */
    fun onRaised(episode: Episode, nowMs: Long, callActive: Boolean): Episode = episode.copy(
        attempts = if (callActive) episode.attempts + 1 else episode.attempts,
        lastAttemptAtMs = nowMs,
        postCallAttemptUsed = episode.postCallAttemptUsed || !callActive,
    )

    /** How long until the next tick. */
    fun nextTickDelayMs(episode: Episode): Long =
        if (episode.attempts >= MAX_ATTEMPTS_PER_CALL && episode.callEndedAtMs == 0L) IDLE_TICK_MS else TICK_MS
}
