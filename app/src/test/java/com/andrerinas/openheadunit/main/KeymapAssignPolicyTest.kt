package com.andrerinas.openheadunit.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeymapAssignPolicyTest {
    private val next = 87
    private val volUp = 24

    private val back = 4

    @Test fun backLeavesTheScreenWithNoRowOpen() {
        assertTrue(KeymapAssignPolicy.passesBack(back, rowOpen = false, doNothingRow = false))
    }

    @Test fun backCancelsTheDoNothingDialog() {
        assertTrue(KeymapAssignPolicy.passesBack(back, rowOpen = true, doNothingRow = true))
    }

    @Test fun anActionRowStillTakesBack() {
        assertFalse(KeymapAssignPolicy.passesBack(back, rowOpen = true, doNothingRow = false))
    }

    @Test fun otherKeysAreNotPassed() {
        assertFalse(KeymapAssignPolicy.passesBack(next, rowOpen = true, doNothingRow = true))
        assertFalse(KeymapAssignPolicy.passesBack(next, rowOpen = false, doNothingRow = false))
    }

    @Test fun rowTakesAKeyFromTheIgnoredSet() {
        val r = KeymapAssignPolicy.assignToRow(emptyMap(), setOf(next), 21, next)
        assertEquals(mapOf(21 to next), r.keyMap)
        assertEquals(emptySet<Int>(), r.ignored)
    }

    @Test fun ignoredTakesAKeyFromItsRow() {
        val r = KeymapAssignPolicy.assignToIgnored(mapOf(21 to next, 22 to volUp), emptySet(), next)
        assertEquals(mapOf(22 to volUp), r.keyMap)
        assertEquals(setOf(next), r.ignored)
    }

    @Test fun aSecondPressFreesTheKey() {
        val r = KeymapAssignPolicy.assignToIgnored(emptyMap(), setOf(next), next)
        assertEquals(emptySet<Int>(), r.ignored)
    }

    @Test fun aKeyOnTwoRowsLeavesBoth() {
        val r = KeymapAssignPolicy.assignToIgnored(mapOf(21 to next, 22 to next, 23 to volUp), emptySet(), next)
        assertEquals(mapOf(23 to volUp), r.keyMap)
        assertEquals(setOf(next), r.ignored)
    }
}
