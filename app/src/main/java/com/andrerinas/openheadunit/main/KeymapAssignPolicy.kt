package com.andrerinas.openheadunit.main

import android.view.KeyEvent

/** Moves a physical key between the action rows and the ignored set, so it lives in one place. */
object KeymapAssignPolicy {

    data class Result(val keyMap: Map<Int, Int>, val ignored: Set<Int>)

    /** An action row may take Back. The Do nothing row may not: Back there cancels, or it would die on the projection. */
    fun passesBack(keyCode: Int, rowOpen: Boolean, doNothingRow: Boolean): Boolean =
        keyCode == KeyEvent.KEYCODE_BACK && (!rowOpen || doNothingRow)

    fun assignToRow(keyMap: Map<Int, Int>, ignored: Set<Int>, row: Int, physical: Int): Result =
        Result(keyMap + (row to physical), ignored - physical)

    /** A key already in the set comes out; that is the only way to free one key without Reset. */
    fun assignToIgnored(keyMap: Map<Int, Int>, ignored: Set<Int>, physical: Int): Result {
        if (physical in ignored) return Result(keyMap, ignored - physical)
        return Result(keyMap.filterValues { it != physical }, ignored + physical)
    }
}
