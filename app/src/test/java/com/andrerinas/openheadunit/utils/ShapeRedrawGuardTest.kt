package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.ScreenOrientationPolicy.Normalisation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShapeRedrawGuardTest {

    @Test
    fun `a fresh guard has redrawn for nothing`() {
        assertNull(ShapeRedrawGuard().redrawnFor)
    }

    @Test
    fun `the redraw's own session keeps the guard`() {
        val guard = ShapeRedrawGuard().afterRedraw(Normalisation.LANDSCAPE).atSessionStart()
        assertEquals(Normalisation.LANDSCAPE, guard.redrawnFor)
    }

    @Test
    fun `a later session clears the guard`() {
        val guard = ShapeRedrawGuard().afterRedraw(Normalisation.LANDSCAPE).atSessionStart().atSessionStart()
        assertNull(guard.redrawnFor)
    }

    @Test
    fun `a session with no redraw before it leaves the guard empty`() {
        assertNull(ShapeRedrawGuard().atSessionStart().redrawnFor)
    }
}
