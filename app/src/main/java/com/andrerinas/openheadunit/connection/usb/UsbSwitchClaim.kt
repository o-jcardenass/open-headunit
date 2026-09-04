package com.andrerinas.openheadunit.connection.usb

/**
 * A process-wide "an AOA switch is running" flag, staked by [UsbAttachedActivity].
 *
 * The activity runs its own switch on its own thread and cannot reach [UsbLauncherManager]: the
 * service may not exist yet, and on a cold start its `onCreate` runs before any intent the activity
 * sends could be delivered - which is exactly when the wireless bring-up decision is made. Both
 * live in the same process, so a static claim is the only thing visible early enough.
 *
 * It expires on its own because the activity is `noHistory` and can be finished mid-switch without
 * ever running its release.
 */
object UsbSwitchClaim {

    @Volatile
    private var claimUntilMs = 0L

    fun stake() {
        claimUntilMs = UsbAccessoryHandoffPolicy.claimExpiryFrom(System.currentTimeMillis())
    }

    fun release() {
        claimUntilMs = 0L
    }

    fun isLive(): Boolean =
        UsbAccessoryHandoffPolicy.switchClaimIsLive(System.currentTimeMillis(), claimUntilMs)
}
