package com.andrerinas.openheadunit.connection.carkey.fyt

/** Raw steering values from the FYT firmware, and the keys they stand for. */
object FytKeyTranslationPolicy {

    private val table = mapOf(
        547 to 23,
        550 to 21,
        551 to 22,
        574 to 87,
        575 to 88,
        587 to 88,
        588 to 87,
    )

    /** A raw value the user mapped or ignored stays raw, so that choice still matches. */
    fun sendCode(raw: Int, keyMap: Map<Int, Int>, ignored: Set<Int>): Int {
        if (keyMap.containsValue(raw) || raw in ignored) return raw
        return table[raw] ?: raw
    }
}
