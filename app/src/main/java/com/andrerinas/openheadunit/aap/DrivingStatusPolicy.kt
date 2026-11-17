package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Sensors.SensorBatch.DrivingStatusData.Status

/**
 * Per-session driving status from GPS speed: enter on 3 fast fixes, leave after 5 s slow.
 * Cheap units report about 0.5 m/s at a standstill and a walk is 1.4 m/s, so a single
 * threshold would flip at every fix. One instance per session, never shared.
 */
class DrivingStatusPolicy {
    private var moving = false
    private var fastFixes = 0
    private var slowSinceMs = NONE
    private var lastFixMs = NONE

    /** Returns the new status only when it changes. A fix without speed or time order changes nothing. */
    @Synchronized
    fun onFix(speedMps: Float?, elapsedMs: Long): Status? {
        if (speedMps == null) return null
        if (lastFixMs != NONE && elapsedMs <= lastFixMs) return null
        lastFixMs = elapsedMs
        if (!moving) {
            fastFixes = if (speedMps >= ENTER_MPS) fastFixes + 1 else 0
            if (fastFixes < ENTER_FIXES) return null
            moving = true
            fastFixes = 0
            slowSinceMs = NONE
            return Status.NO_VIDEO
        }
        if (speedMps >= LEAVE_MPS) {
            slowSinceMs = NONE
            return null
        }
        if (slowSinceMs == NONE) slowSinceMs = elapsedMs
        if (elapsedMs - slowSinceMs < LEAVE_HOLD_MS) return null
        moving = false
        slowSinceMs = NONE
        return Status.UNRESTRICTED
    }

    /** Leaves `moving` once the newest fix with speed is [STALE_FIX_MS] old. Returns UNRESTRICTED once, else null. */
    @Synchronized
    fun onTick(nowMs: Long): Status? {
        if (!moving || lastFixMs == NONE || nowMs - lastFixMs < STALE_FIX_MS) return null
        moving = false
        slowSinceMs = NONE
        return Status.UNRESTRICTED
    }

    /** What a late sender, such as a channel that opens after the first fix, must send. */
    @Synchronized
    fun current(): Status = if (moving) Status.NO_VIDEO else Status.UNRESTRICTED

    /** Back to the start state. Returns UNRESTRICTED when that ends a NO_VIDEO, else null. */
    @Synchronized
    fun reset(): Status? {
        val wasMoving = moving
        moving = false
        fastFixes = 0
        slowSinceMs = NONE
        lastFixMs = NONE
        return if (wasMoving) Status.UNRESTRICTED else null
    }

    companion object {
        const val ENTER_MPS = 2.0f
        const val ENTER_FIXES = 3
        const val LEAVE_MPS = 0.5f
        const val LEAVE_HOLD_MS = 5000L
        const val STALE_FIX_MS = 10_000L
        private const val NONE = Long.MIN_VALUE
    }
}
