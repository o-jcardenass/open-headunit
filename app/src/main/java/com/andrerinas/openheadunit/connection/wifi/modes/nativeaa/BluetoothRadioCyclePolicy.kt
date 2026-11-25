package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Whether to raise the Bluetooth event Android Auto needs by cycling this unit's own radio, rather
 * than by poking over a hands-free link the unit already holds.
 *
 * A poke takes the phone's single hands-free slot and cannot give it back. A cycle drops the ACL
 * instead, so the unit's own stack reconnects and the phone sees `ACL_CONNECTED` and the hands-free
 * `CONNECTION_STATE_CHANGED` for an address that is connected with its profile: Gearhead's trigger
 * and its presence gate, with no slot taken from anyone.
 */
object BluetoothRadioCyclePolicy {

    /** Last release where `BluetoothAdapter.disable()` does anything; from 33 it is a no-op. */
    const val LAST_CYCLEABLE_SDK = 32

    /** One per arming. A cycle costs every Bluetooth device on the unit a few seconds. */
    const val MAX_CYCLES_PER_ARMING = 1

    /** Shared with the poke it replaces, so a phone that is merely slow is never disturbed twice. */
    const val CYCLE_AFTER_MS = HandsFreeWakeEscalationPolicy.ESCALATE_AFTER_MS
    const val CYCLE_COOLDOWN_MS = HandsFreeWakeEscalationPolicy.ESCALATION_COOLDOWN_MS

    /** How long the unit's own stack is given to reconnect before the cycle is judged. */
    const val RECOVERY_WINDOW_MS = NativeAaWakeDamagePolicy.PROBE_WINDOW_MS

    /**
     * How long the device is left alone afterwards. Longer than the recovery window because the
     * phone has to act on the event too: the one measured poke-to-dial-back took 66 s, and a poke
     * 15 s after the cycle would take back the link it just handed over.
     */
    const val POST_CYCLE_QUIET_MS = 90_000L

    /** The same for a pick: past the probe's reading, so it stays honest, and inside the wake's ceiling. */
    const val CHOSEN_QUIET_MS = RECOVERY_WINDOW_MS + 1_000L

    /** What is left of the quiet that ends at [quietUntilMs] for the automatic loop. */
    fun quietLeftMs(quietUntilMs: Long, nowMs: Long, chosenWake: Boolean): Long {
        val until = if (chosenWake) quietUntilMs - POST_CYCLE_QUIET_MS + CHOSEN_QUIET_MS else quietUntilMs
        return (until - nowMs).coerceAtLeast(0L)
    }

    /** Bounds on the two adapter transitions, so a radio that never answers cannot hang the loop. */
    const val ADAPTER_OFF_WAIT_MS = 10_000L
    const val ADAPTER_ON_WAIT_MS = 20_000L

    /** How long a cycle may keep the radio off: the OFF wait, then the ON wait. */
    const val CYCLE_RADIO_DOWN_MS = ADAPTER_OFF_WAIT_MS + ADAPTER_ON_WAIT_MS

    /**
     * How long a start() that finds the radio off waits for a cycle to bring it back, from the
     * cycle's [radioBackByMs] stamp (0 when none runs). 0 means start() gives up at once.
     */
    fun startWaitMs(now: Long, radioBackByMs: Long): Long =
        if (radioBackByMs <= 0L) 0L else (radioBackByMs - now).coerceIn(0L, CYCLE_RADIO_DOWN_MS)

    /** The most a start() waits: a cancel can run its own OFF and ON waits after the cycle's. */
    const val CYCLED_RADIO_WAIT_CAP_MS = 2 * CYCLE_RADIO_DOWN_MS

    /**
     * The start() wait's next deadline. A cancelled cycle can push [radioBackByMs] later, so the
     * deadline follows it, capped from [startedAtMs]. 0 means the cycle is over: stop waiting.
     */
    fun cycleWaitDeadline(startedAtMs: Long, deadlineMs: Long, radioBackByMs: Long): Long =
        if (radioBackByMs <= 0L) 0L
        else maxOf(deadlineMs, radioBackByMs).coerceAtMost(startedAtMs + CYCLED_RADIO_WAIT_CAP_MS)

    enum class AfterCycleWait { RESUME, ENABLE, GIVE_UP }

    /** The wait never turns the radio on: only the user's enable option, where supported, does. */
    fun afterCycleWait(radioOn: Boolean, enableOption: Boolean, enableSupported: Boolean): AfterCycleWait = when {
        radioOn -> AfterCycleWait.RESUME
        enableOption && enableSupported -> AfterCycleWait.ENABLE
        else -> AfterCycleWait.GIVE_UP
    }

    /**
     * `TARGET_CONNECTED` and `NO_LINK` may cycle. `TARGET_ABSENT` may not: another device holds the
     * link, and the adapter-wide cycle would drop it. Every gate in [cycleDecision] still applies.
     */
    val CYCLEABLE_REASONS: Set<BluetoothWakePolicy.WakeReason> = setOf(
        BluetoothWakePolicy.WakeReason.TARGET_CONNECTED,
        BluetoothWakePolicy.WakeReason.NO_LINK,
    )

    /** What this unit is known to do with its own Bluetooth after a cycle. */
    enum class Verdict {
        /** Never measured. The next cycle is the probe. */
        UNKNOWN,

        /** The radio came back and reconnected the hands-free link on its own. */
        RECOVERS,

        /** The radio or the link did not come back. Nothing cycles on this unit again. */
        STRANDS;

        companion object {
            /** Stored as an int so an unknown value from a newer build reads as unmeasured. */
            fun of(stored: Int): Verdict = entries.getOrNull(stored) ?: UNKNOWN
        }
    }

    /** What a stand-down pass does about a hands-free link that raises no event. */
    enum class StandDownAction { NOTHING, CYCLE_RADIO, ESCALATED_WAKE }

    /**
     * The one place the two levers are chosen between, so they can never both run for one pass.
     * The cycle goes first: it gives the link back where the wake cannot. [wakeAllowed] is a lambda
     * because asking it spends the wake's budget and arms its probe, which a chosen cycle must not.
     */
    fun chooseAction(cycleAllowed: Boolean, wakeAllowed: () -> Boolean): StandDownAction = when {
        cycleAllowed -> StandDownAction.CYCLE_RADIO
        wakeAllowed() -> StandDownAction.ESCALATED_WAKE
        else -> StandDownAction.NOTHING
    }

    /**
     * Whether to cycle the radio for a stand-down that has run long enough.
     *
     * @param reason why [BluetoothWakePolicy.wakeDecision] refused. Only the reasons in
     *   [CYCLEABLE_REASONS] cycle: TARGET_UNREADABLE could not tell whose link it is, and a guess is
     *   not a reason to drop it.
     * @param pairingHasRunAaHere a phone paired only for calls is never disturbed.
     * @param callActive a call rides on the link this would drop.
     * @param sessionInProgress a session up, handshaking or settling; nothing touches the radio then.
     * @param standingInForHfp the cycle closes the stand-in Hands-Free record the phone needs here.
     * @param waitMs how long the evidence has to have been accumulating. [CYCLE_AFTER_MS] for the
     *   stand-down case, whose link deserves its run; the dead-link path passes its own, shorter
     *   floor because there is no link there to give a run to.
     */
    fun cycleDecision(
        sdkInt: Int,
        verdict: Verdict,
        reason: BluetoothWakePolicy.WakeReason,
        pairingHasRunAaHere: Boolean,
        callActive: Boolean,
        sessionInProgress: Boolean,
        standingInForHfp: Boolean,
        standDownSinceMs: Long,
        lastCycleMs: Long,
        cyclesUsed: Int,
        now: Long,
        waitMs: Long = CYCLE_AFTER_MS,
    ): Boolean = blocker(
        sdkInt, verdict, reason, pairingHasRunAaHere, callActive, sessionInProgress, standingInForHfp,
        standDownSinceMs, lastCycleMs, cyclesUsed, now, waitMs,
    ) == null

    /**
     * The gate that refused, or null when the cycle is allowed.
     *
     * A refusal used to be silent, and that cost real time on the rig: a wake loop poked a phone
     * with no link for minutes, the policy declined every pass, and the log said nothing about
     * which condition was unsatisfied. Naming the gate makes the same trace one line.
     */
    enum class Blocker {
        SDK_TOO_NEW,
        RADIO_STRANDS,
        REASON_NOT_CYCLEABLE,
        PAIRING_NEVER_RAN_AA,
        CALL_ACTIVE,
        SESSION_IN_PROGRESS,
        STANDING_IN_FOR_HFP,
        BUDGET_SPENT,
        TOO_SOON,
        COOLDOWN,
    }

    fun blocker(
        sdkInt: Int,
        verdict: Verdict,
        reason: BluetoothWakePolicy.WakeReason,
        pairingHasRunAaHere: Boolean,
        callActive: Boolean,
        sessionInProgress: Boolean,
        standingInForHfp: Boolean,
        standDownSinceMs: Long,
        lastCycleMs: Long,
        cyclesUsed: Int,
        now: Long,
        waitMs: Long = CYCLE_AFTER_MS,
    ): Blocker? {
        if (sdkInt > LAST_CYCLEABLE_SDK) return Blocker.SDK_TOO_NEW
        if (verdict == Verdict.STRANDS) return Blocker.RADIO_STRANDS
        if (reason !in CYCLEABLE_REASONS) return Blocker.REASON_NOT_CYCLEABLE
        if (!pairingHasRunAaHere) return Blocker.PAIRING_NEVER_RAN_AA
        if (callActive) return Blocker.CALL_ACTIVE
        if (sessionInProgress) return Blocker.SESSION_IN_PROGRESS
        if (standingInForHfp) return Blocker.STANDING_IN_FOR_HFP
        if (cyclesUsed >= MAX_CYCLES_PER_ARMING) return Blocker.BUDGET_SPENT
        if (standDownSinceMs <= 0L || now - standDownSinceMs < waitMs) return Blocker.TOO_SOON
        if (lastCycleMs > 0L && now - lastCycleMs < CYCLE_COOLDOWN_MS) return Blocker.COOLDOWN
        return null
    }

    /** Whether this cycle is the one being measured. */
    fun isProbe(verdict: Verdict): Boolean = verdict == Verdict.UNKNOWN

    /**
     * What the cycle did. A radio that never came back is recorded even when the link cannot be read.
     * @param linkWasAbsent the cycle answered `NO_LINK`: the phone may be off or away, so a link still
     *   missing afterwards says nothing about this unit's radio and must not record `STRANDS`.
     */
    fun verdictFrom(
        adapterCameBackOn: Boolean?,
        linkReturned: Boolean?,
        linkWasAbsent: Boolean = false,
    ): Verdict = when {
        adapterCameBackOn == false -> Verdict.STRANDS
        adapterCameBackOn == null -> Verdict.UNKNOWN
        linkReturned == true -> Verdict.RECOVERS
        linkWasAbsent -> Verdict.UNKNOWN
        linkReturned == false -> Verdict.STRANDS
        else -> Verdict.UNKNOWN
    }

    /** Whether a probe result is worth storing. An unreadable one would only overwrite a measurement. */
    fun recordable(verdict: Verdict): Boolean = verdict != Verdict.UNKNOWN
}
