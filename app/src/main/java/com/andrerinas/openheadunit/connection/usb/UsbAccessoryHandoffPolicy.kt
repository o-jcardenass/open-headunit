package com.andrerinas.openheadunit.connection.usb

/**
 * Timings for the window between `ACC_REQ_START` and the accessory interface being claimed.
 *
 * A dongle holds accessory mode only until something claims it - measured at 390 ms on a
 * Carlinkit-class unit, where a phone waits indefinitely. Two things cost us that window: the
 * re-enumerated 0x2D00 has no permission yet for the few hundred ms before the manifest-matched
 * activity launch grants it, and the attach fallback timer is far longer than the dongle's
 * patience. Everything here is a budget for waiting, not a rule about which device to take, which
 * is [UsbAttachPolicy]'s question.
 */
object UsbAccessoryHandoffPolicy {

    /** Re-check `hasPermission` on this cadence rather than raising a dialog straight away. */
    const val PERMISSION_POLL_INTERVAL_MS = 200L

    /**
     * Give up and ask the user after this long. Must stay under
     * [UsbLauncherManager.ATTACH_FALLBACK_DELAY_MS] so the retry resolves before the fallback that
     * would start a competing switch.
     */
    const val PERMISSION_POLL_BUDGET_MS = 1_000L

    /** How often the switching thread looks for the re-enumerated accessory device. */
    const val DEVICE_POLL_INTERVAL_MS = 100L

    /** How long to look before concluding the device never came back in accessory mode. */
    const val DEVICE_POLL_BUDGET_MS = 3_000L

    /**
     * How long a switch claim stays live without being released. The activity that stakes it is
     * `noHistory`, so it can be finished mid-switch and never run its release.
     */
    const val SWITCH_CLAIM_TTL_MS = 5_000L

    fun shouldKeepPollingForPermission(elapsedMs: Long): Boolean =
        elapsedMs < PERMISSION_POLL_BUDGET_MS

    fun shouldKeepPollingForDevice(elapsedMs: Long): Boolean =
        elapsedMs < DEVICE_POLL_BUDGET_MS

    /** A claim staked at [claimUntilMs] is honoured until it expires, so a dead claimer frees it. */
    fun switchClaimIsLive(nowMs: Long, claimUntilMs: Long): Boolean = nowMs < claimUntilMs

    fun claimExpiryFrom(nowMs: Long): Long = nowMs + SWITCH_CLAIM_TTL_MS
}
