package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When repeated re-banking stops being recovery and starts being a message about the cushion. */
class AudioUnderrunDeepenTriggerTest {

    @Test
    fun `a sink that re-banks now and then is left alone`() {
        assertFalse(AudioUnderrunRecoveryPolicy.shouldDeepen(0))
        assertFalse(AudioUnderrunRecoveryPolicy.shouldDeepen(2))
    }

    @Test
    fun `three in a minute says the cushion is too shallow`() {
        assertTrue(AudioUnderrunRecoveryPolicy.shouldDeepen(3))
        assertTrue(AudioUnderrunRecoveryPolicy.shouldDeepen(9))
    }

    @Test
    fun `deepening is offered before giving up, never after it alone`() {
        assertTrue(
            "a sink must get the chance to climb out before it is left shallow",
            AudioUnderrunRecoveryPolicy.DEEPEN_AFTER_REBANKS <
                AudioUnderrunRecoveryPolicy.MAX_REBANKS_PER_MINUTE
        )
        assertTrue(AudioUnderrunRecoveryPolicy.shouldDeepen(AudioUnderrunRecoveryPolicy.MAX_REBANKS_PER_MINUTE))
    }
}
