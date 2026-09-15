package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioUnderrunRecoveryPolicyTest {

    @Test
    fun `no underrun is no reason to gap the audio`() {
        assertFalse(AudioUnderrunRecoveryPolicy.shouldRebank(0, Long.MAX_VALUE, 0))
    }

    @Test
    fun `the first underrun re-banks`() {
        assertTrue(AudioUnderrunRecoveryPolicy.shouldRebank(1, Long.MAX_VALUE, 0))
    }

    @Test
    fun `a second underrun inside the spacing waits`() {
        assertFalse(
            AudioUnderrunRecoveryPolicy.shouldRebank(
                underrunsSinceLastCheck = 3,
                sinceLastRebankMs = AudioUnderrunRecoveryPolicy.MIN_SPACING_MS - 1,
                rebanksInLastMinute = 1
            )
        )
        assertTrue(
            AudioUnderrunRecoveryPolicy.shouldRebank(
                underrunsSinceLastCheck = 3,
                sinceLastRebankMs = AudioUnderrunRecoveryPolicy.MIN_SPACING_MS,
                rebanksInLastMinute = 1
            )
        )
    }

    @Test
    fun `a link that is simply short stops being re-banked`() {
        assertFalse(
            AudioUnderrunRecoveryPolicy.shouldRebank(
                underrunsSinceLastCheck = 10,
                sinceLastRebankMs = Long.MAX_VALUE,
                rebanksInLastMinute = AudioUnderrunRecoveryPolicy.MAX_REBANKS_PER_MINUTE
            )
        )
        assertTrue(AudioUnderrunRecoveryPolicy.hasGivenUp(AudioUnderrunRecoveryPolicy.MAX_REBANKS_PER_MINUTE))
        assertFalse(AudioUnderrunRecoveryPolicy.hasGivenUp(AudioUnderrunRecoveryPolicy.MAX_REBANKS_PER_MINUTE - 1))
    }
}
