package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Whether a phone refused this unit's hands-free connection outright, and how long to keep asking.
 *
 * Measured on a Z Fold3 (2026-10-22 in the car, 2026-10-23 at home): with two other Bluetooth audio
 * devices connected - the car's own Bluetooth or a headset, plus earbuds - every wake poke was taken
 * and dropped. The phone logged "okToAcceptConnection: Blocked by isNewConnectionAcceptable" and the
 * whole link was gone 0.34 s later, because it holds two audio devices at a time and One UI offers
 * no setting to raise that. Android Auto opened wireless setup and gave up after 5 s, which is all
 * the driver saw, and the wake round ran out three tries later with nothing on this unit's screen.
 * With one device put away the same poke reached a session in 12 s.
 *
 * So a refusal is named on the main screen, and the phone is asked again, slowly: by a chosen
 * phone's wake for its ordinary rounds, then by the automatic loop, so other phones get a turn.
 * Putting the earbuds away has to be all it takes.
 */
object HandsFreeRefusalPolicy {

    /** When the phone's link is read after the poke connects. The Fold dropped it within 0.34 s. */
    const val LINK_PROBE_MS = 2_000L

    /** How long a refusing phone is asked at [RETRY_GAP_MS] after the first refusal of a wake. */
    const val PATIENCE_MS = 10 * 60_000L

    /** The gap between asks while it refuses: each ask opens Android Auto on the phone for 5 s. */
    const val RETRY_GAP_MS = 30_000L

    /** The gap after [PATIENCE_MS]: the asking slows rather than stops. */
    const val SLOW_RETRY_GAP_MS = 2 * 60_000L

    /**
     * Refused, as opposed to silent: the hands-free walk got no answer and the phone's whole link
     * went with it. A phone already serving another device's link stays connected and says nothing,
     * and a link that cannot be read is never taken as gone.
     */
    fun refused(stage: HfpSlcInitiator.Stage, linkAfterProbe: BluetoothWakePolicy.TargetLink): Boolean =
        (stage == HfpSlcInitiator.Stage.IDLE || stage == HfpSlcInitiator.Stage.BRSF) &&
            linkAfterProbe == BluetoothWakePolicy.TargetLink.ABSENT

    /** A phone's refusals since it last took the link, both on the `elapsedRealtime` clock. */
    data class Refusals(val firstAtMs: Long, val lastAtMs: Long)

    /** One more refusal at [nowMs]. Silence longer than [PATIENCE_MS] starts the cadence again. */
    fun noted(previous: Refusals?, nowMs: Long): Refusals =
        if (previous == null || nowMs - previous.lastAtMs > PATIENCE_MS) Refusals(nowMs, nowMs)
        else previous.copy(lastAtMs = nowMs)

    /** Whether a phone still refuses. A record of one that left or was unpaired ages out. */
    fun anyLive(records: Collection<Refusals>, nowMs: Long): Boolean =
        records.any { nowMs - it.lastAtMs <= PATIENCE_MS }

    /** When the automatic loop may ask a phone that refused again; at least [RETRY_GAP_MS] later. */
    fun askAgainAtMs(refusals: Refusals): Long =
        refusals.lastAtMs + gapMs(refusals.firstAtMs, refusals.lastAtMs, RETRY_GAP_MS)

    /**
     * Whether a chosen phone's wake gives its slot to the automatic loop, which asks it again and
     * wakes the others too. Only a refusal earns that, and never for a phone no longer paired.
     */
    fun handsOver(
        refused: Boolean,
        outcome: BluetoothWakePolicy.WakeOutcome,
        ordinaryRoundsLeft: Boolean,
    ): Boolean = refused && !ordinaryRoundsLeft && outcome != BluetoothWakePolicy.WakeOutcome.NOT_PAIRED

    /** A refusing pick that the wake list left out when it was picked, and that list. */
    data class OffListPick(val mac: String, val list: PokeTargets, val nextAskAtMs: Long)

    /** Null when the automatic loop wakes [mac] anyway. */
    fun offListPick(mac: String, list: PokeTargets, nowMs: Long): OffListPick? = when {
        list is PokeTargets.Selected && mac in list.macs -> null
        list == PokeTargets.AllPaired -> null
        else -> OffListPick(mac, list, nowMs + RETRY_GAP_MS)
    }

    /**
     * Whether the automatic loop still asks [pick], [PATIENCE_MS] from the pick at most. A new pick
     * or a changed list ends it, as does a link the phone took ([stillRefuses] false).
     */
    fun asksOffList(
        pick: OffListPick,
        chosenMac: String?,
        list: PokeTargets,
        stillRefuses: Boolean,
        pickAgeMs: Long,
    ): Boolean = stillRefuses && pickAgeMs < PATIENCE_MS && list == pick.list &&
        pick.mac.equals(chosenMac, ignoreCase = true)

    /**
     * The gap after an ask of an off-list pick, refused or not. A dial nothing answered holds the
     * loop for up to 40 s, so with other phones on the list it is rarer.
     */
    fun offListGapMs(unanswered: Boolean, othersListed: Boolean): Long =
        if (unanswered && othersListed) SLOW_RETRY_GAP_MS else RETRY_GAP_MS

    /**
     * The gap before the next ask: [ordinaryGapMs] until the phone refuses, then [RETRY_GAP_MS] for
     * [PATIENCE_MS], then [SLOW_RETRY_GAP_MS].
     */
    fun gapMs(firstRefusalAtMs: Long, nowMs: Long, ordinaryGapMs: Long): Long = when {
        firstRefusalAtMs == 0L -> ordinaryGapMs
        nowMs - firstRefusalAtMs < PATIENCE_MS -> RETRY_GAP_MS
        else -> SLOW_RETRY_GAP_MS
    }
}
