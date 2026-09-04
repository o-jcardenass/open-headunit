package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Which Bluetooth route the Native AA handshake takes on this unit.
 *
 * [com.andrerinas.openheadunit.utils.ExternalBtPolicy] answers a different question — whether this
 * unit's Bluetooth is an external module — and the two must not be conflated. Detection identifies
 * a *class of hardware*; this decides what to *do* about it, and the sets are not the same. A unit
 * can carry every external-BT marker and still have nothing listening on the vendor daemon's port,
 * because at least one vendor family in that class reaches its module over Binder instead. So
 * detection alone can never be a reason to take the module route.
 *
 * That is why reachability is not an input here. Whether the daemon answers is decided by trying
 * it, not by predicting it. This object says which route is *worth attempting*; the transport
 * reports what happened when it did.
 *
 * Pure, because the same decision is read at four call sites — `WifiLauncherNative.start`,
 * `NativeAaHandshakeManager.start`, `checkCompatibility` and the settings UI — and they drifted
 * apart once already when the condition was written out longhand at each of them.
 */
object ExternalBtTransportPolicy {

    enum class Route {
        /** This unit's own Bluetooth. What every unit without external-BT markers does. */
        NORMAL,

        /** The external module, through the vendor daemon. */
        ZBT,

        /** External Bluetooth, and no route through it: refuse mode 3 and say why. */
        BLOCKED
    }

    /**
     * The module route wins over the compatibility override when both are on.
     *
     * They are two different escapes from the same detection. `nativeAaIgnoreExternalBt` means
     * "use this unit's own radio anyway", and its own setting comment concedes it is unlikely to
     * help: on the unit examined closely the radio accepted every write, flushed, and put nothing
     * on the air. The module route is the one that can actually carry bytes, so a user who asked
     * for both gets it.
     *
     * @param externalBtEvidence what `ExternalBtPolicy.detect` found, or null on ordinary hardware
     * @param zbtTransportEnabled the user's opt-in, `Settings.externalBtZbtTransport`
     * @param ignoreExternalBt the compatibility override, `Settings.nativeAaIgnoreExternalBt`
     */
    fun route(
        externalBtEvidence: String?,
        zbtTransportEnabled: Boolean,
        ignoreExternalBt: Boolean
    ): Route = when {
        externalBtEvidence == null -> Route.NORMAL
        zbtTransportEnabled -> Route.ZBT
        ignoreExternalBt -> Route.NORMAL
        else -> Route.BLOCKED
    }
}
