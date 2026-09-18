package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionGeometryLockPolicyTest {

    @Test
    fun `an announced canvas is kept, because no message can re-send it`() {
        assertEquals(
            SessionGeometryLockPolicy.Verdict.KEEP,
            SessionGeometryLockPolicy.onOrientationMismatch(canvasAnnounced = true)
        )
    }

    @Test
    fun `nothing announced yet means re-deriving is free`() {
        assertEquals(
            SessionGeometryLockPolicy.Verdict.RENEGOTIATE,
            SessionGeometryLockPolicy.onOrientationMismatch(canvasAnnounced = false)
        )
    }

    @Test
    fun `a lever, once one exists, is what lifts the announced case`() {
        assertEquals(
            SessionGeometryLockPolicy.Verdict.RENEGOTIATE,
            SessionGeometryLockPolicy.onOrientationMismatch(
                canvasAnnounced = true,
                renegotiationAvailable = true
            )
        )
    }

    @Test
    fun `no lever ships today, so the default is the announced-keeps case`() {
        assertFalse(SessionGeometryLockPolicy.RENEGOTIATION_AVAILABLE)
        assertEquals(
            SessionGeometryLockPolicy.onOrientationMismatch(canvasAnnounced = true),
            SessionGeometryLockPolicy.onOrientationMismatch(
                canvasAnnounced = true,
                renegotiationAvailable = SessionGeometryLockPolicy.RENEGOTIATION_AVAILABLE
            )
        )
    }
}
