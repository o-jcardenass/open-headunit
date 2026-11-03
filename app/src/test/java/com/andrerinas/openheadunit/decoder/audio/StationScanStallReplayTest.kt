package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class StationScanStallReplayTest {
    // Media packet arrival times (ms) from a head unit whose station scanned for ~1.47 s every ~15 s.
    private val arrivals: List<Long> = javaClass.classLoader!!
        .getResourceAsStream("station_scan_stall_arrivals_ms.txt")!!
        .bufferedReader().readLines().filter { it.isNotBlank() }.map { it.trim().toLong() }

    @Test fun `catch-up bursts after repeated scan stalls discard no music`() {
        val buffer = AdaptivePcmBuffer(latencyMultiplier = 16, isMediaSink = true)
        val packet = ShortArray(1024 * 2) { ((it % 97) * 200 - 9600).toShort() }
        val out = ShortArray(480 * 2)
        var next = 0
        for (now in 0L..arrivals.last() + 2000) {
            while (next < arrivals.size && arrivals[next] <= now) {
                buffer.noteArrival(now, 1024)
                buffer.write(packet, packet.size, now)
                next++
            }
            if (now % 10 == 0L) buffer.render(out, now)
        }
        assertEquals(0L, buffer.droppedFrames)
    }
}
