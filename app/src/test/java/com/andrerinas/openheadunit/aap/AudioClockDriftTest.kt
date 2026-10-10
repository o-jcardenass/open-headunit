package com.andrerinas.openheadunit.aap

import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class AudioClockDriftTest {
    private val frameUs = 42_667L

    private fun run(drift: AudioClockDrift, ppm: Double, seconds: Int): AudioClockDrift.Report? {
        val random = Random(7)
        var last: AudioClockDrift.Report? = null
        var source = 1_000_000L
        while (source < 1_000_000L + seconds * 1_000_000L) {
            val elapsed = source - 1_000_000L
            val arrival = 5_000_000_000L + elapsed + (elapsed * ppm / 1e6).toLong() + random.nextInt(40_000)
            drift.onPacket(source, arrival)?.let { last = it }
            source += frameUs
        }
        return drift.estimate() ?: last
    }

    @Test fun `a fast phone clock reads as its drift despite link jitter`() {
        val report = run(AudioClockDrift(), 100.0, 300)!!
        assertEquals(100.0, report.ppm, 10.0)
    }

    @Test fun `matched clocks read as zero`() {
        assertEquals(0.0, run(AudioClockDrift(), 0.0, 300)!!.ppm, 10.0)
    }

    @Test fun `a slow phone clock reads negative`() {
        assertEquals(-80.0, run(AudioClockDrift(), -80.0, 300)!!.ppm, 10.0)
    }

    @Test fun `fewer than three windows reports nothing`() {
        val drift = AudioClockDrift()
        assertNull(run(drift, 100.0, 25))
        assertNull(drift.estimate())
    }

    @Test fun `a start resets the estimate`() {
        val drift = AudioClockDrift()
        assertNotNull(run(drift, 100.0, 300))
        drift.reset()
        assertNull(drift.estimate())
    }

    @Test fun `the periodic report fires once a minute`() {
        val drift = AudioClockDrift()
        var reports = 0
        var source = 1_000_000L
        while (source < 1_000_000L + 200_000_000L) {
            if (drift.onPacket(source, 5_000_000_000L + source) != null) reports++
            source += frameUs
        }
        assertTrue("reports=$reports", reports in 2..4)
    }

    @Test fun `unknown timestamps and a clock rollback do not poison the fit`() {
        val drift = AudioClockDrift()
        assertNull(drift.onPacket(0, 1_000_000))
        drift.onPacket(1_000_000, 5_000_000)
        drift.onPacket(1_000_000 + frameUs, 4_000_000)
        assertNull(drift.estimate())
    }

    @Test fun `the report line names sign and windows`() {
        assertEquals("clock drift=+100 ppm over 30s (3 windows)", AudioClockDrift.Report(100.2, 30, 3).toString())
        assertEquals("clock drift=-5 ppm over 30s (3 windows)", AudioClockDrift.Report(-5.1, 30, 3).toString())
    }
}
