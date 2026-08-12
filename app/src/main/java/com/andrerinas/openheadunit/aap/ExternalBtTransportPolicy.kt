package com.andrerinas.openheadunit.aap

/**
 * Which Bluetooth route the Native AA handshake takes on this unit.
 *
 * [ExternalBtPolicy] answers a different question — whether this unit's Bluetooth is an external
 * module — and the two must not be conflated. Detection identifies a *class of hardware*; this
 * decides what to *do* about it, and the sets are not the same. A unit can carry every external-BT
 * marker and still have nothing listening on the vendor daemon's port, because at least one vendor
 * family in that class reaches its module over Binder instead. So detection alone can never be a
 * reason to take the module route.
 *
 * That is why reachability is not an input here. Whether the daemon answers is decided by trying
 * it, not by predicting it, and the attempt is the only honest test. This object says which route
 * is *worth attempting*; the transport reports what happened when it did.
 *
 * Pure, because the same decision is read at four call sites — `AapService.initWifiMode`,
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
     * A manually named secondary Bluetooth service used to be an override here: it meant "serve
     * this unit over `android.bluetooth` anyway". It is not one any more.
     *
     * That escape hatch existed because naming a second *Android* radio was the only thing a user
     * on this hardware could try. On a unit whose Bluetooth really is an external module it does
     * not help and cannot: the phone is bonded to a chip no Android adapter reaches, so forcing
     * the handshake onto one produces the original failure — writes that succeed, a log that looks
     * healthy, and nothing on the air. It offered a way to make the app *attempt* a connection,
     * which is not the same as a way to connect, and the module transport is the real answer for
     * this hardware.
     *
     * The setting itself is unaffected and still resolves a genuine second radio on units that
     * have one; it is simply no longer consulted here.
     *
     * @param externalBtEvidence what [ExternalBtPolicy.detect] found, or null on ordinary hardware
     * @param zbtTransportEnabled the user's opt-in, `Settings.externalBtZbtTransport`
     */
    fun route(
        externalBtEvidence: String?,
        zbtTransportEnabled: Boolean
    ): Route = when {
        externalBtEvidence == null -> Route.NORMAL
        zbtTransportEnabled -> Route.ZBT
        else -> Route.BLOCKED
    }
}
