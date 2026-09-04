package com.andrerinas.openheadunit.connection.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The budgets that decide whether we win the accessory-mode window. Measured against a dongle that
 * held accessory mode for 390 ms on one attempt and 1.3 s on the next.
 */
class UsbAccessoryHandoffPolicyTest {

    @Test
    fun `permission is re-checked while the budget lasts`() {
        assertTrue(UsbAccessoryHandoffPolicy.shouldKeepPollingForPermission(elapsedMs = 0L))
        assertTrue(UsbAccessoryHandoffPolicy.shouldKeepPollingForPermission(elapsedMs = 999L))
    }

    @Test
    fun `permission polling stops at the budget, so the dialog is still reachable`() {
        assertFalse(UsbAccessoryHandoffPolicy.shouldKeepPollingForPermission(elapsedMs = 1_000L))
        assertFalse(UsbAccessoryHandoffPolicy.shouldKeepPollingForPermission(elapsedMs = 5_000L))
    }

    /**
     * The retry has to resolve before the attach fallback would fire, or the fallback starts a
     * second AOA switch on a device we are already waiting on.
     */
    @Test
    fun `the permission budget fits inside the attach fallback delay`() {
        assertTrue(
            UsbAccessoryHandoffPolicy.PERMISSION_POLL_BUDGET_MS <
                UsbLauncherManager.ATTACH_FALLBACK_DELAY_MS
        )
    }

    @Test
    fun `the device poll outlasts a slow re-enumeration`() {
        assertTrue(UsbAccessoryHandoffPolicy.shouldKeepPollingForDevice(elapsedMs = 1_300L))
        assertTrue(UsbAccessoryHandoffPolicy.shouldKeepPollingForDevice(elapsedMs = 2_999L))
    }

    @Test
    fun `the device poll gives up at its budget`() {
        assertFalse(UsbAccessoryHandoffPolicy.shouldKeepPollingForDevice(elapsedMs = 3_000L))
    }

    @Test
    fun `a fresh claim is live and an expired one is not`() {
        val now = 10_000L
        val until = UsbAccessoryHandoffPolicy.claimExpiryFrom(now)

        assertTrue(UsbAccessoryHandoffPolicy.switchClaimIsLive(nowMs = now, claimUntilMs = until))
        assertTrue(UsbAccessoryHandoffPolicy.switchClaimIsLive(nowMs = until - 1, claimUntilMs = until))
        assertFalse(UsbAccessoryHandoffPolicy.switchClaimIsLive(nowMs = until, claimUntilMs = until))
    }

    /** A released claim is stored as 0, which must never read as live. */
    @Test
    fun `a released claim is not live`() {
        assertFalse(UsbAccessoryHandoffPolicy.switchClaimIsLive(nowMs = 0L, claimUntilMs = 0L))
        assertFalse(UsbAccessoryHandoffPolicy.switchClaimIsLive(nowMs = 1_000L, claimUntilMs = 0L))
    }

    /**
     * The claim outlives the switch it guards, so an activity finished by `noHistory` before it
     * could release cannot leave the fallback disarmed for less than the switch it was covering.
     */
    @Test
    fun `the claim TTL outlasts the device poll it covers`() {
        assertTrue(
            UsbAccessoryHandoffPolicy.SWITCH_CLAIM_TTL_MS >
                UsbAccessoryHandoffPolicy.DEVICE_POLL_BUDGET_MS
        )
    }
}
