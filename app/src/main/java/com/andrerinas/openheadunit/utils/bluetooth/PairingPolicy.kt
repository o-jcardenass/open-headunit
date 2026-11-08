package com.andrerinas.openheadunit.utils.bluetooth

/**
 * Rules for pairing a new phone from the unit: prerequisites, bond states, visible time and
 * entry gate. Inputs are plain ints, longs and booleans so no Android class loads in a test.
 */
object PairingPolicy {

    const val VISIBLE_SECONDS = 120
    const val MAJOR_PHONE = 0x0200

    const val BOND_NONE = 10
    const val BOND_BONDING = 11
    const val BOND_BONDED = 12

    /** Search end or prompt grant stays useful this long for the system pairing dialog gate. */
    private const val DIALOG_GATE_MS = 60_000L
    private const val ASKING_HINT_MS = 20_000L
    private const val MIN_SDK = 19
    private const val FRESH_SEARCH_SDK = 26
    private const val NEARBY_SDK = 31
    private const val LOCATION_SDK = 23
    private const val DEFAULT_RADIO = "bluetooth_manager"

    enum class Prerequisite { BLUETOOTH_ON, NEARBY_DEVICES, FINE_LOCATION, LOCATION_ON, VISIBLE }

    enum class BondEvent { ASKING, FAILED, DONE, IGNORE }

    enum class Kind { PHONE, OTHER }

    enum class VisibleRoute { SYSTEM_PROMPT, NONE }

    enum class PromptLaunch { OK, NO_ACTIVITY, REFUSED }

    fun prerequisites(
        sdk: Int,
        btOn: Boolean,
        hasNearbyPermission: Boolean,
        hasFineLocation: Boolean,
        locationOn: Boolean,
        visible: Boolean
    ): List<Prerequisite> = buildList {
        if (!btOn) add(Prerequisite.BLUETOOTH_ON)
        if (sdk >= NEARBY_SDK && !hasNearbyPermission) add(Prerequisite.NEARBY_DEVICES)
        if (sdk >= LOCATION_SDK && !hasFineLocation) add(Prerequisite.FINE_LOCATION)
        if (sdk >= LOCATION_SDK && !locationOn) add(Prerequisite.LOCATION_ON)
        if (!visible) add(Prerequisite.VISIBLE)
    }

    fun bondTransition(previous: Int, current: Int): BondEvent = when {
        previous == current -> BondEvent.IGNORE
        current == BOND_BONDED && (previous == BOND_BONDING || previous == BOND_NONE) -> BondEvent.DONE
        current == BOND_BONDING && previous == BOND_NONE -> BondEvent.ASKING
        current == BOND_NONE && previous == BOND_BONDING -> BondEvent.FAILED
        else -> BondEvent.IGNORE
    }

    fun kindOf(majorClass: Int?): Kind = if (majorClass == MAJOR_PHONE) Kind.PHONE else Kind.OTHER

    /** Adds a device once by address, takes a changed name, keeps first-seen order, skips bonded. */
    fun mergeFound(list: List<PairingRow>, device: FoundDevice): List<PairingRow> {
        if (device.bonded) return list
        val at = list.indexOfFirst { it.address == device.address }
        if (at < 0) {
            return list + PairingRow(device.address, device.name, kindOf(device.majorClass), RowState.IDLE)
        }
        val row = list[at]
        val name = device.name ?: row.name
        if (name == row.name) return list
        return list.toMutableList().also { it[at] = row.copy(name = name) }
    }

    fun remainingMs(grantAtMs: Long, grantedSeconds: Int, nowMs: Long): Long =
        maxOf(0L, grantAtMs + grantedSeconds * 1000L - nowMs)

    fun visibleRoute(promptAvailable: Boolean): VisibleRoute =
        if (promptAvailable) VisibleRoute.SYSTEM_PROMPT else VisibleRoute.NONE

    /** Only a missing activity closes the prompt route; a refused launch waits for the permission. */
    fun latchesPromptUnavailable(result: PromptLaunch): Boolean = result == PromptLaunch.NO_ACTIVITY

    fun needsFreshSearch(sdk: Int, msSinceDiscoveryEnd: Long?): Boolean =
        sdk >= FRESH_SEARCH_SDK && (msSinceDiscoveryEnd == null || msSinceDiscoveryEnd > DIALOG_GATE_MS)

    fun needsFreshPrompt(sdk: Int, msSincePromptGrant: Long?, visibleSeconds: Int): Boolean =
        sdk in MIN_SDK until FRESH_SEARCH_SDK &&
            (msSincePromptGrant == null || msSincePromptGrant > visibleSeconds * 1000L + DIALOG_GATE_MS)

    fun askingHint(msAsking: Long): Boolean = msAsking > ASKING_HINT_MS

    fun entryAvailable(
        sdk: Int,
        externalModule: Boolean,
        routeBlocked: Boolean,
        radioServiceName: String,
        adapterPresent: Boolean,
        sessionLive: Boolean
    ): Boolean = sdk >= MIN_SDK &&
        !externalModule &&
        !routeBlocked &&
        (radioServiceName.isEmpty() || radioServiceName == DEFAULT_RADIO) &&
        adapterPresent &&
        !sessionLive

    /** The screen keeps the settings hold while the unit is visible or a bond is in flight. */
    fun holdsSettings(visibleMsLeft: Long, bondInFlight: Boolean): Boolean =
        visibleMsLeft > 0 || bondInFlight
}
