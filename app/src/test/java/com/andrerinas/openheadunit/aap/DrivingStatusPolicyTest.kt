package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors.SensorBatch.DrivingStatusData.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingStatusPolicyTest {

    private fun movingPolicy(): DrivingStatusPolicy {
        val policy = DrivingStatusPolicy()
        policy.onFix(3.0f, 1000)
        policy.onFix(3.0f, 2000)
        assertEquals(Status.NO_VIDEO, policy.onFix(3.0f, 3000))
        return policy
    }

    @Test fun startsUnrestricted() {
        assertEquals(Status.UNRESTRICTED, DrivingStatusPolicy().current())
    }

    @Test fun twoFastFixesDoNotEnterThreeDo() {
        val policy = DrivingStatusPolicy()
        assertNull(policy.onFix(2.0f, 1000))
        assertNull(policy.onFix(2.5f, 2000))
        assertEquals(Status.UNRESTRICTED, policy.current())
        assertEquals(Status.NO_VIDEO, policy.onFix(2.0f, 3000))
        assertEquals(Status.NO_VIDEO, policy.current())
    }

    @Test fun aSlowFixBreaksTheFastRun() {
        val policy = DrivingStatusPolicy()
        policy.onFix(3.0f, 1000)
        policy.onFix(3.0f, 2000)
        policy.onFix(1.9f, 3000)
        assertNull(policy.onFix(3.0f, 4000))
        assertEquals(Status.UNRESTRICTED, policy.current())
    }

    @Test fun standstillNoiseNeverEnters() {
        val policy = DrivingStatusPolicy()
        for (i in 1..50) assertNull(policy.onFix(0.6f, i * 1000L))
        assertEquals(Status.UNRESTRICTED, policy.current())
    }

    @Test fun aStopOfFourSecondsStaysMovingFiveLeaves() {
        val policy = movingPolicy()
        assertNull(policy.onFix(0.0f, 4000))
        assertNull(policy.onFix(0.0f, 8000))
        assertEquals(Status.NO_VIDEO, policy.current())
        assertEquals(Status.UNRESTRICTED, policy.onFix(0.0f, 9000))
        assertEquals(Status.UNRESTRICTED, policy.current())
    }

    @Test fun aFixAboveLeaveSpeedRestartsTheHold() {
        val policy = movingPolicy()
        policy.onFix(0.0f, 4000)
        assertNull(policy.onFix(1.0f, 7000))
        assertNull(policy.onFix(0.0f, 8000))
        assertNull(policy.onFix(0.0f, 12000))
        assertEquals(Status.UNRESTRICTED, policy.onFix(0.0f, 13000))
    }

    @Test fun aFixWithoutSpeedChangesNothing() {
        val policy = movingPolicy()
        assertNull(policy.onFix(null, 4000))
        assertNull(policy.onFix(null, 20000))
        assertEquals(Status.NO_VIDEO, policy.current())
    }

    @Test fun aRepeatedFixTimeIsNotCountedTwice() {
        val policy = DrivingStatusPolicy()
        policy.onFix(3.0f, 1000)
        assertNull(policy.onFix(3.0f, 1000))
        assertNull(policy.onFix(3.0f, 1000))
        assertEquals(Status.UNRESTRICTED, policy.current())
    }

    @Test fun aNewInstanceStartsUnrestricted() {
        movingPolicy()
        assertEquals(Status.UNRESTRICTED, DrivingStatusPolicy().current())
    }

    @Test fun resetEndsNoVideoOnce() {
        val policy = movingPolicy()
        assertEquals(Status.UNRESTRICTED, policy.reset())
        assertEquals(Status.UNRESTRICTED, policy.current())
        assertNull(policy.reset())
    }

    @Test fun noFixForTenSecondsWhileMovingLeavesOnce() {
        val policy = movingPolicy()
        assertNull(policy.onTick(12_999))
        assertEquals(Status.NO_VIDEO, policy.current())
        assertEquals(Status.UNRESTRICTED, policy.onTick(13_000))
        assertEquals(Status.UNRESTRICTED, policy.current())
        assertNull(policy.onTick(16_000))
    }

    @Test fun aFixWithoutSpeedDoesNotKeepMovingAlive() {
        val policy = movingPolicy()
        policy.onFix(null, 8000)
        assertEquals(Status.UNRESTRICTED, policy.onTick(13_000))
    }

    @Test fun aTickWhileNotMovingChangesNothing() {
        val policy = DrivingStatusPolicy()
        policy.onFix(3.0f, 1000)
        assertNull(policy.onTick(60_000))
        assertEquals(Status.UNRESTRICTED, policy.current())
    }

    @Test fun aFreshFixKeepsMovingThroughTicks() {
        val policy = movingPolicy()
        assertNull(policy.onFix(3.0f, 12_000))
        assertNull(policy.onTick(21_000))
        assertEquals(Status.NO_VIDEO, policy.current())
    }
}
