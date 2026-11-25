package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RendererConfirmPolicyTest {

    @Test
    fun `the bar counts only while the user could see it over a picture`() {
        assertTrue(RendererConfirmPolicy.seen(onScreen = true, frameRendered = true, noticeUp = false, nightOverlayUp = false))
        assertFalse(RendererConfirmPolicy.seen(onScreen = false, frameRendered = true, noticeUp = false, nightOverlayUp = false))
        assertFalse(RendererConfirmPolicy.seen(onScreen = true, frameRendered = false, noticeUp = false, nightOverlayUp = false))
        assertFalse(RendererConfirmPolicy.seen(onScreen = true, frameRendered = true, noticeUp = true, nightOverlayUp = false))
        assertFalse(RendererConfirmPolicy.seen(onScreen = true, frameRendered = true, noticeUp = false, nightOverlayUp = true))
    }

    @Test
    fun `ten seen seconds close the bar`() {
        var remaining = RendererConfirmPolicy.SHOW_MS
        var ticks = 0
        while (remaining > 0L) {
            remaining = RendererConfirmPolicy.remainingMs(remaining, seen = true)
            ticks++
        }
        assertEquals(10, ticks)
    }

    @Test
    fun `a bar nobody saw never closes by itself`() {
        var remaining = RendererConfirmPolicy.SHOW_MS
        repeat(100) { remaining = RendererConfirmPolicy.remainingMs(remaining, seen = false) }
        assertEquals(RendererConfirmPolicy.SHOW_MS, remaining)
    }
}
