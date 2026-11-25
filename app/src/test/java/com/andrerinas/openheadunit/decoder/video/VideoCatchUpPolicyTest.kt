package com.andrerinas.openheadunit.decoder.video

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCatchUpPolicyTest {
    private fun offers(
        readyCount: Int = 3,
        index: Int = 0,
        timing: Boolean = true,
        deadline: Long = 0L,
        repair: Boolean = false,
        rendered: Boolean = true,
    ) = VideoCatchUpPolicy.offers(readyCount, index, timing, deadline, repair, rendered)

    @Test fun `one or two ready frames always show, late or not`() {
        for (timing in listOf(true, false)) {
            assertTrue(offers(readyCount = 1, timing = timing))
            assertTrue(offers(readyCount = 2, index = 0, timing = timing))
            assertTrue(offers(readyCount = 2, index = 1, timing = timing))
        }
    }

    @Test fun `newest slot of a burst always shows`() {
        assertTrue(offers(readyCount = 3, index = 2))
        assertTrue(offers(readyCount = 10, index = 9, timing = false))
    }

    @Test fun `without the timing controller a burst shows only its newest slot`() {
        assertFalse(offers(timing = false, deadline = 0L))
        assertFalse(offers(timing = false, deadline = 5_000_000L))
        assertFalse(offers(timing = false, repair = true))
        assertFalse(offers(timing = false, rendered = false))
    }

    @Test fun `under the timing controller a late superseded slot of a burst is skipped`() {
        assertFalse(offers(readyCount = 3, index = 0))
        assertFalse(offers(readyCount = 3, index = 1))
        assertFalse(offers(readyCount = 10, index = 8))
    }

    @Test fun `under the timing controller a slot inside its window keeps its turn`() {
        assertTrue(offers(deadline = 1L))
        assertTrue(offers(readyCount = 10, index = 3, deadline = 2_000_000_000L))
    }

    @Test fun `a timing drop is left to admission`() {
        assertTrue(offers(deadline = Long.MIN_VALUE))
    }

    @Test fun `a repair candidate or the first picture is never skipped`() {
        assertTrue(offers(repair = true))
        assertTrue(offers(rendered = false))
    }
}
