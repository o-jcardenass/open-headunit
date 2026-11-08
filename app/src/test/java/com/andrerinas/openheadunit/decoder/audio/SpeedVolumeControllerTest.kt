package com.andrerinas.openheadunit.decoder.audio

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeedVolumeControllerTest {

    private class FakeVolume(var index: Int, val min: Int = 0, val max: Int = 15) : VolumePort {
        val adjusts = mutableListOf<Boolean>()
        var reads = 0
        override fun index(stream: Int): Int { reads++; return index }
        override fun adjust(stream: Int, raise: Boolean) {
            adjusts += raise
            index = (index + if (raise) 1 else -1).coerceIn(min, max)
        }
    }

    private class FakeStore : LevelStore {
        var value: StoredLevel? = null
        override fun load() = value
        override fun save(value: StoredLevel) { this.value = value }
    }

    private class Rig(startIndex: Int = 5, val store: FakeStore = FakeStore(), max: Int = 15) {
        val volume = FakeVolume(startIndex, max = max)
        var speed: Float? = 0f
        var speedReads = 0
        var now = 0L
        val lines = mutableListOf<String>()
        val scope = TestScope()
        val controller = SpeedVolumeController(
            volume, SpeedSource { speedReads++; speed }, store, { now }, { lines += it.removePrefix("SpeedVolumeController: ") }
        )
        fun start(stream: Int = 3, sink: Boolean = true, on: Boolean = true) =
            controller.start(SessionSnapshot(stream, sink, on), scope)
        fun tickAt(seconds: Long, kmh: Float?) { now = seconds * 1000; speed = kmh; controller.tick() }
        fun count(prefix: String) = lines.count { it.startsWith(prefix) }
    }

    @Test
    fun `each event logs once`() {
        val rig = Rig()
        rig.start()
        rig.tickAt(0, 0f)
        rig.tickAt(2, 80f)
        assertEquals(listOf("started stream 3 index 5", "base level 0 at 0 km/h"), rig.lines.take(2))
        assertEquals("level 0 -> 1 at 80 km/h, step UP on stream 3, index 5 -> 6", rig.lines[2])
        assertEquals(3, rig.lines.size)
    }

    @Test
    fun `a lost fix logs once and a returning fix logs once`() {
        val rig = Rig()
        rig.start()
        rig.tickAt(0, 0f)
        repeat(5) { rig.tickAt(2L + it * 2, null) }
        rig.tickAt(14, 0f)
        assertEquals(1, rig.count("no fix, holding"))
        assertEquals(1, rig.count("fix back"))
        assertEquals(1, rig.count("base level"))
    }

    @Test
    fun `the switch off touches nothing`() {
        val rig = Rig()
        rig.start(on = false)
        rig.controller.tick()
        assertEquals(listOf("disabled"), rig.lines)
        assertEquals(0, rig.volume.reads)
        assertTrue(rig.volume.adjusts.isEmpty())
    }

    @Test
    fun `the sink off touches nothing`() {
        val rig = Rig()
        rig.start(sink = false)
        rig.controller.tick()
        assertEquals(listOf("disabled: audio sink off"), rig.lines)
        assertEquals(0, rig.volume.reads)
        assertTrue(rig.volume.adjusts.isEmpty())
    }

    @Test
    fun `a restart with an empty store takes a base and stop moves nothing`() {
        val rig = Rig()
        rig.start()
        rig.tickAt(0, 80f)
        rig.controller.stop()
        assertTrue(rig.volume.adjusts.isEmpty())
        rig.store.value = null
        rig.start()
        rig.tickAt(10, 80f)
        assertEquals(2, rig.count("base level 2 at 80 km/h"))
        assertTrue(rig.volume.adjusts.isEmpty())
    }

    @Test
    fun `at the maximum index an up then a down move nothing`() {
        val rig = Rig(startIndex = 15, max = 15)
        rig.start()
        rig.tickAt(0, 0f)
        rig.tickAt(2, 80f)
        rig.tickAt(14, 0f)
        assertEquals(1, rig.volume.adjusts.size)
        assertEquals(1, rig.count("step UP on stream 3 no change"))
        assertEquals(1, rig.count("step DOWN skipped, held level 0"))
        assertEquals(15, rig.volume.index)
    }

    @Test
    fun `a knob turn keeps the level and a later session ends at the same index`() {
        val rig = Rig(startIndex = 5)
        rig.start()
        rig.tickAt(0, 0f)
        rig.tickAt(2, 50f)
        rig.tickAt(12, 80f)
        rig.tickAt(22, 110f)
        assertEquals(8, rig.volume.index)
        rig.volume.index = 5
        rig.tickAt(24, 110f)
        assertEquals(1, rig.count("manual change, index 8 -> 5"))
        assertEquals(5, rig.volume.index)
        rig.tickAt(34, 0f)
        rig.tickAt(44, 0f)
        rig.tickAt(54, 0f)
        assertEquals(2, rig.volume.index)
        rig.controller.stop()
        rig.start()
        rig.tickAt(60, 110f)
        assertEquals(5, rig.volume.index)
    }

    @Test
    fun `a stored level resumes at once`() {
        val store = FakeStore().apply { value = StoredLevel(3, 7, 2) }
        val rig = Rig(startIndex = 7, store = store)
        rig.start()
        rig.tickAt(0, 0f)
        assertEquals(listOf(false, false), rig.volume.adjusts)
        assertEquals(5, rig.volume.index)
        assertEquals(1, rig.count("resume level 2 -> 0 at 0 km/h, index 7 -> 5"))
    }

    @Test
    fun `the first step after a resume waits for the hold`() {
        val store = FakeStore().apply { value = StoredLevel(3, 7, 2) }
        val rig = Rig(startIndex = 7, store = store)
        rig.start()
        rig.tickAt(0, 38f)
        assertEquals(listOf(false, false), rig.volume.adjusts)
        assertEquals(5, rig.volume.index)
        rig.tickAt(2, 44f)
        rig.tickAt(9, 44f)
        assertEquals(2, rig.volume.adjusts.size)
        rig.tickAt(10, 44f)
        assertEquals(listOf(false, false, true), rig.volume.adjusts)
        assertEquals(1, rig.count("level 0 -> 1 at 44 km/h, step UP on stream 3, index 5 -> 6"))
    }

    @Test
    fun `a resume that moves nothing arms no hold`() {
        val store = FakeStore().apply { value = StoredLevel(3, 7, 2) }
        val rig = Rig(startIndex = 7, store = store)
        rig.start()
        rig.tickAt(0, 80f)
        assertTrue(rig.volume.adjusts.isEmpty())
        rig.tickAt(2, 104f)
        assertEquals(listOf(true), rig.volume.adjusts)
        assertEquals(1, rig.count("level 2 -> 3 at 104 km/h, step UP on stream 3, index 7 -> 8"))
    }

    @Test
    fun `a stored level at the matching speed moves nothing`() {
        val store = FakeStore().apply { value = StoredLevel(3, 7, 2) }
        val rig = Rig(startIndex = 7, store = store)
        rig.start()
        rig.tickAt(0, 80f)
        assertTrue(rig.volume.adjusts.isEmpty())
    }

    @Test
    fun `a different index or stream gives a base`() {
        for (stored in listOf(StoredLevel(3, 6, 2), StoredLevel(2, 7, 2))) {
            val rig = Rig(startIndex = 7, store = FakeStore().apply { value = stored })
            rig.start()
            rig.tickAt(0, 0f)
            assertTrue(rig.volume.adjusts.isEmpty())
            assertEquals(1, rig.count("base level 0 at 0 km/h"))
        }
    }

    @Test
    fun `a new controller on the same store resumes like a process restart`() {
        val first = Rig(startIndex = 5)
        first.start()
        first.tickAt(0, 0f)
        first.tickAt(2, 50f)
        first.tickAt(12, 80f)
        assertEquals(7, first.volume.index)
        val second = Rig(startIndex = 7, store = first.store)
        second.start()
        second.tickAt(0, 0f)
        assertEquals(5, second.volume.index)
    }

    @Test
    fun `the snapshot is read at start, so a change waits for the next start`() {
        val rig = Rig()
        rig.start()
        rig.tickAt(0, 0f)
        rig.tickAt(2, 80f)
        assertEquals(1, rig.volume.adjusts.size)
        rig.start(sink = false)
        val reads = rig.volume.reads
        rig.tickAt(14, 130f)
        assertEquals(reads, rig.volume.reads)
        assertEquals(1, rig.volume.adjusts.size)
        assertEquals(1, rig.count("disabled: audio sink off"))
    }

    @Test
    fun `starting twice runs one tick loop`() {
        val rig = Rig()
        rig.start()
        rig.start()
        rig.scope.runCurrent()
        rig.scope.testScheduler.advanceTimeBy(2_001)
        rig.scope.runCurrent()
        assertEquals(2, rig.speedReads)
        rig.controller.stop()
    }

    @Test
    fun `stop logs once for a running controller and not for a disabled one`() {
        val running = Rig()
        running.start()
        running.controller.stop()
        running.controller.stop()
        assertEquals(1, running.count("stopped"))

        val off = Rig()
        off.start(on = false)
        off.controller.stop()
        assertEquals(0, off.count("stopped"))
    }

    @Test
    fun `the ramp trace gives the expected step lines`() {
        val rig = Rig(startIndex = 5)
        rig.start()
        var t = 0L
        fun run(seconds: Int, speedAt: (Int) -> Float?) {
            for (i in 0 until seconds / 2) { rig.tickAt(t, speedAt(i)); t += 2 }
        }
        run(20) { 0f }
        run(26) { (it + 1) * 10f }
        run(40) { 130f }
        run(40) { 80f }
        run(60) { if (it % 2 == 0) 69f else 71f }
        run(90) { null }
        run(20) { 70f }
        run(14) { (6 - it) * 10f }
        run(20) { 0f }
        rig.controller.stop()
        val steps = rig.lines.filter { it.startsWith("level") }
        assertEquals(
            listOf(
                "level 0 -> 1 at 50 km/h, step UP on stream 3, index 5 -> 6",
                "level 1 -> 2 at 100 km/h, step UP on stream 3, index 6 -> 7",
                "level 2 -> 3 at 130 km/h, step UP on stream 3, index 7 -> 8",
                "level 3 -> 4 at 130 km/h, step UP on stream 3, index 8 -> 9",
                "level 4 -> 3 at 80 km/h, step DOWN on stream 3, index 9 -> 8",
                "level 3 -> 2 at 80 km/h, step DOWN on stream 3, index 8 -> 7",
                "level 2 -> 1 at 60 km/h, step DOWN on stream 3, index 7 -> 6",
                "level 1 -> 0 at 10 km/h, step DOWN on stream 3, index 6 -> 5"
            ),
            steps
        )
        assertEquals(1, rig.count("no fix, holding"))
        assertEquals(1, rig.count("fix back"))
        assertEquals(1, rig.count("base level"))
        assertEquals(1, rig.count("stopped"))
    }
}
