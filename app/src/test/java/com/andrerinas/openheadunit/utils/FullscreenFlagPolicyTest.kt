package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenFlagPolicyTest {

    @Test
    fun `the projection keeps the flag on every version below 30`() {
        for (sdk in 16..29) assertTrue("$sdk", FullscreenFlagPolicy.adds(sdk, resizesForKeyboard = false))
    }

    @Test
    fun `a window that resizes for the keyboard has the flag only below 19`() {
        for (sdk in 16..18) assertTrue("$sdk", FullscreenFlagPolicy.adds(sdk, resizesForKeyboard = true))
        for (sdk in 19..29) assertFalse("$sdk", FullscreenFlagPolicy.adds(sdk, resizesForKeyboard = true))
    }

    @Test
    fun `no window has it from 30`() {
        assertFalse(FullscreenFlagPolicy.adds(30, resizesForKeyboard = false))
        assertFalse(FullscreenFlagPolicy.adds(36, resizesForKeyboard = true))
    }
}
