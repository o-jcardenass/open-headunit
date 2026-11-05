package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class LogExportCopyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun logFiles(dir: File) = dir.listFiles().orEmpty().map { it.name }.toSet()

    @Test
    fun liveCaptureThatHasNotRolledNeedsACopy() {
        assertTrue(LogExporter.exportNeedsCopy(capturing = true, rolled = false))
    }

    @Test
    fun rolledCaptureNeedsACopyWhetherLiveOrStopped() {
        assertTrue(LogExporter.exportNeedsCopy(capturing = true, rolled = true))
        assertTrue(LogExporter.exportNeedsCopy(capturing = false, rolled = true))
    }

    @Test
    fun stoppedCaptureThatNeverRolledExportsItsOwnFile() {
        assertFalse(LogExporter.exportNeedsCopy(capturing = false, rolled = false))
    }

    @Test
    fun copyOutlivesTheSegmentItCameFrom() {
        val dir = tempFolder.newFolder()
        val segment = File(dir, "segment.txt").apply { writeText("before export\n") }

        val copy = LogExporter.copySegments(dir, null, segment)
        segment.appendText("after export\n")
        segment.delete()

        assertNotEquals(segment, copy)
        assertEquals("before export\n", copy.readText())
    }

    @Test
    fun copyPutsTheEarlierSegmentFirstAndLeavesBothInPlace() {
        val dir = tempFolder.newFolder()
        val earlier = File(dir, "earlier.txt").apply { writeText("old\n") }
        val source = File(dir, "source.txt").apply { writeText("new\n") }

        val copy = LogExporter.copySegments(dir, earlier, source)

        assertEquals("old\nnew\n", copy.readText())
        assertEquals("old\n", earlier.readText())
        assertEquals("new\n", source.readText())
    }

    @Test
    fun failedCopyLeavesNoPartialFile() {
        val dir = tempFolder.newFolder()
        val earlier = File(dir, "earlier.txt").apply { writeText("old\n") }
        val before = logFiles(dir)

        try {
            LogExporter.copySegments(dir, earlier, File(dir, "gone.txt"))
            throw AssertionError("a missing segment must fail the copy")
        } catch (_: IOException) {
        }

        assertEquals(before, logFiles(dir))
    }
}
