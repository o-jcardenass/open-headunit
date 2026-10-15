package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleAnswerStagePolicyTest {
    @Test
    fun aReportedPhoneMovesThePill() {
        assertTrue(ModuleAnswerStagePolicy.reportsPhoneAnswered(true))
    }

    @Test
    fun aBlindAttemptDoesNot() {
        assertFalse(ModuleAnswerStagePolicy.reportsPhoneAnswered(false))
    }
}
