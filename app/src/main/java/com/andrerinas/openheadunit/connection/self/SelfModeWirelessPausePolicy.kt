package com.andrerinas.openheadunit.connection.self

/**
 * Whether the wireless stack may arm itself while Self Mode is running.
 *
 * Self Mode projects this device to itself over loopback, so a P2P group and a poke loop serve
 * nothing and cost plenty: a poke's own `socket.connect()` raises the `ACL_CONNECTED` that re-inits
 * the mode, and it takes the target phone's hands-free link. The WiFi button passes, so a wireless
 * connection the user asks for by hand is still the way back up.
 */
object SelfModeWirelessPausePolicy {

    /**
     * @param selfModeArmed a launch is armed or still running its launchers.
     * @param loopbackSessionLive `CommManager.isLoopbackSession`. Asked beside the flag rather than
     *                            instead of it: the flag covers the launch, the session covers a
     *                            launch whose flag was cleared by its own timeout.
     */
    fun refusesBringUp(
        selfModeArmed: Boolean,
        loopbackSessionLive: Boolean,
        userRequested: Boolean
    ): Boolean = (selfModeArmed || loopbackSessionLive) && !userRequested
}
