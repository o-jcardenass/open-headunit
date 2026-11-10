package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class StationScanStallReplayTest {
    // Media packet arrival times (ms) from a head unit whose station scanned for ~1.47 s every ~15 s.
    private val arrivals: List<Long> = resource("station_scan_stall_arrivals_ms.txt")

    // One station-scan sweep of 2048-frame packets, with ten gaps of 199 to 489 ms.
    private val sweep: List<Long> = resource("scan_sweep_arrivals_ms.txt")

    private fun resource(name: String): List<Long> = javaClass.classLoader!!
        .getResourceAsStream(name)!!
        .bufferedReader().readLines().filter { it.isNotBlank() }.map { it.trim().toLong() }

    private class Outcome(val bank: AdaptivePcmBuffer, val starvations: Int, val targets: Map<Long, Int>)

    private fun replay(arr: LongArray, packetFrames: Int, media: Boolean = true, tailMs: Long = 3000,
                       probes: Set<Long> = emptySet()): Outcome {
        val bank = AdaptivePcmBuffer(latencyMultiplier = 16, isMediaSink = media)
        val packet = ShortArray(packetFrames * 2) { ((it % 97) * 200 - 9600).toShort() }
        val out = ShortArray(960)
        var next = 0
        var starvations = 0
        val targets = HashMap<Long, Int>()
        val end = arr.last()
        for (now in 0L..end + tailMs) {
            while (next < arr.size && arr[next] <= now) {
                bank.noteArrival(now, packetFrames)
                bank.write(packet, packet.size, now)
                next++
            }
            if (now % 10 == 0L) {
                bank.render(out, now, 960)
                // The end of the data starves once more, and that one is not a real stall.
                if (bank.takeStarvation() != null && now <= end) starvations++
                if (now in probes) targets[now] = bank.targetFrames()
            }
        }
        return Outcome(bank, starvations, targets)
    }

    // A paced stream of 2048-frame packets with a solid stall at each start; the backlog then bursts in.
    private fun schedule(totalMs: Long, stalls: List<Pair<Long, Long>>): LongArray {
        val out = ArrayList<Long>()
        val pace = 2048 * 1000.0 / 48000
        var index = 0L
        var t = 0.0
        var last = 0L
        var stall = 0
        var hold = -1L
        while (t < totalMs) {
            var at = t.toLong()
            while (stall < stalls.size && at >= stalls[stall].first) {
                hold = stalls[stall].first + stalls[stall].second
                stall++
            }
            if (at < hold) at = hold
            if (at <= last) at = last + (if (hold > 0 && at < hold + 2000) 2 else 0)
            if (at < last) at = last
            out.add(at)
            last = at
            index++
            t = index * pace
        }
        return out.toLongArray()
    }

    private fun step(periodMs: Long, count: Int, sizes: List<Long>, firstMs: Long = 80_000) =
        schedule(firstMs + periodMs * count + 30_000,
            (0 until count).map { (firstMs + it * periodMs) to sizes[it % sizes.size] })

    private fun sweepRepeated(times: Int): LongArray {
        val window = sweep.filter { it in 37_000 until 197_000 }.map { it - 37_000 }
        return LongArray(window.size * times) { window[it % window.size] + (it / window.size) * 160_000L }
    }

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

    @Test fun `a repeating scan sweep starves the bank once`() {
        val result = replay(sweepRepeated(8), 2048)
        assertEquals(1, result.starvations)
        assertEquals(0L, result.bank.droppedFrames)
    }

    @Test fun `eight solid stalls of 960 ms starve the bank once`() {
        assertEquals(1, replay(step(160_000, 8, listOf(960)), 2048).starvations)
    }

    @Test fun `mixed stalls at a 160 s step starve four times`() {
        val sizes = listOf(380L, 830, 630, 490, 960, 1600)
        assertEquals(4, replay(step(160_000, 12, sizes), 2048).starvations)
    }

    @Test fun `one group move does not pin the reserve through small dips`() {
        val stalls = listOf(60_000L to 1606L) + (1..14).map { (60_000L + it * 90_000L) to 330L }
        val result = replay(schedule(60_000L + 15 * 90_000L, stalls), 2048,
            probes = setOf(180_000L, 360_000L, 660_000L))
        assert(result.targets.getValue(180_000L) >= 1900 * 48) { "120s=${result.targets}" }
        assert(result.targets.getValue(360_000L) < 1500 * 48) { "300s=${result.targets}" }
        assertEquals(400 * 48, result.targets.getValue(660_000L))
        assertEquals(400 * 48, result.bank.targetFrames())
    }

    @Test fun `a group move then 700 ms scans returns to the scan depth`() {
        val stalls = listOf(60_000L to 1606L) + (0 until 8).map { (220_000L + it * 160_000L) to 700L }
        val result = replay(schedule(220_000L + 8 * 160_000L + 30_000L, stalls), 2048, probes = setOf(370_000L))
        assert(result.targets.getValue(370_000L) < 1300 * 48) { "370s=${result.targets}" }
        assert(result.bank.targetFrames() <= 1100 * 48) { "end=${result.bank.targetFrames()}" }
    }

    @Test fun `the rig join schedule releases the depth above the latest stall`() {
        val sizes = listOf(700L, 1000, 800, 1600, 700, 900)
        val result = replay(step(120_000, 12, sizes), 2048, probes = setOf(470_000L, 670_000L, 1_390_000L))
        assertEquals(4, result.starvations)
        assertEquals(0L, result.bank.droppedFrames)
        assert(result.targets.getValue(470_000L) >= 1900 * 48) { "470s=${result.targets}" }
        assert(result.targets.getValue(670_000L) < 1500 * 48) { "670s=${result.targets}" }
        assert(result.targets.getValue(1_390_000L) < 1500 * 48) { "1390s=${result.targets}" }
    }

    @Test fun `twenty 1_3 s stalls 15 s apart starve the bank once`() {
        val result = replay(step(15_000, 20, listOf(1300), firstMs = 45_000), 2048)
        assertEquals(1, result.starvations)
        assertEquals(0L, result.bank.droppedFrames)
    }

    @Test fun `alternating 1_3 s and 1_7 s stalls starve the bank twice`() {
        val result = replay(step(15_000, 20, listOf(1300, 1700), firstMs = 45_000), 2048)
        assertEquals(2, result.starvations)
        assertEquals(0L, result.bank.droppedFrames)
    }

    @Test fun `the T3 replay still drops nothing with the reserve on`() {
        assertEquals(0L, replay(arrivals.toLongArray(), 1024).bank.droppedFrames)
    }

    @Test fun `a single stall then a clean link returns the target to the base value`() {
        val bank = replay(schedule(700_000, listOf(80_000L to 960L)), 2048).bank
        assertEquals(400 * 48, bank.targetFrames())
    }

    @Test fun `a media bank with no starvation keeps the base target`() {
        val bank = replay(schedule(120_000, emptyList()), 2048).bank
        assertEquals(400 * 48, bank.targetFrames())
    }

    @Test fun `a guidance bank gets no reserve from a stall`() {
        val bank = replay(schedule(300_000, listOf(80_000L to 960L)), 2048, media = false).bank
        assert(bank.targetFrames() <= 400 * 48) { "target=${bank.targetFrames()}" }
    }

    @Test fun `a held bank keeps its target through finish and new arrivals`() {
        val bank = AdaptivePcmBuffer(latencyMultiplier = 16, isMediaSink = true)
        val packet = ShortArray(2048 * 2) { 1000 }
        val out = ShortArray(960)
        val arr = schedule(100_000, listOf(60_000L to 960L))
        var next = 0
        var now = 0L
        fun run(until: Long) {
            while (now < until) {
                while (next < arr.size && arr[next] <= now) {
                    bank.noteArrival(now, 2048); bank.write(packet, packet.size, now); next++
                }
                if (now % 10 == 0L) bank.render(out, now, 960)
                now++
            }
        }
        run(70_000)
        val held = bank.targetFrames()
        assert(held > 400 * 48) { "held=$held" }
        bank.finish()
        run(75_000)
        assertEquals(held, bank.targetFrames())
    }

    @Test fun `a media bank at a 1100 ms target and depth keeps its depth`() {
        val bank = replay(step(160_000, 1, listOf(960)), 2048, tailMs = 60_000).bank
        assertEquals(0L, bank.compressedFrames)
    }
}
