package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotRunningNoticePolicyTest {

    @Test
    fun `a stopped manager with a reason and nothing in flight shows the toast`() {
        assertTrue(NotRunningNoticePolicy.shows(measuringDaemon = false, attemptInFlight = false, notStartedReason = "off"))
        assertTrue(NotRunningNoticePolicy.shows(measuringDaemon = false, attemptInFlight = null, notStartedReason = "off"))
    }

    @Test
    fun `no toast while an attempt is in flight or the daemon is measured`() {
        assertFalse(NotRunningNoticePolicy.shows(measuringDaemon = false, attemptInFlight = true, notStartedReason = "waiting"))
        assertFalse(NotRunningNoticePolicy.shows(measuringDaemon = true, attemptInFlight = false, notStartedReason = "waiting"))
    }

    @Test
    fun `no toast without a reason`() {
        assertFalse(NotRunningNoticePolicy.shows(measuringDaemon = false, attemptInFlight = false, notStartedReason = null))
    }
}
