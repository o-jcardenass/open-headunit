package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.utils.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchCoordinateMapperTest {

    @Test
    fun `touch mapping uses actual input surface width`() {
        val point = TouchCoordinateMapper.map(
            rawX = 1200f,
            rawY = 540f,
            inputSurfaceWidth = 2400f,
            inputSurfaceHeight = 1080f,
            negotiatedWidth = 1920,
            negotiatedHeight = 1080,
            marginWidth = 0f,
            marginHeight = 0f,
            fitMode = Settings.VideoFitMode.FILL,
            hudMirroring = false
        )

        assertEquals(960, point.x)
        assertEquals(540, point.y)
    }

    @Test
    fun `touch mapping accounts for android auto vertical margin`() {
        val point = TouchCoordinateMapper.map(
            rawX = 1200f,
            rawY = 540f,
            inputSurfaceWidth = 2400f,
            inputSurfaceHeight = 1080f,
            negotiatedWidth = 1920,
            negotiatedHeight = 1080,
            marginWidth = 0f,
            marginHeight = 194f,
            fitMode = Settings.VideoFitMode.FILL,
            hudMirroring = false
        )

        assertEquals(960, point.x)
        assertEquals(443, point.y)
    }

    @Test
    fun `touch mapping mirrors horizontal coordinates for hud mirroring`() {
        val point = TouchCoordinateMapper.map(
            rawX = 240f,
            rawY = 540f,
            inputSurfaceWidth = 2400f,
            inputSurfaceHeight = 1080f,
            negotiatedWidth = 1920,
            negotiatedHeight = 1080,
            marginWidth = 0f,
            marginHeight = 0f,
            fitMode = Settings.VideoFitMode.FILL,
            hudMirroring = true
        )

        assertEquals(1728, point.x)
        assertEquals(540, point.y)
    }

    @Test
    fun `contain mode clamps touches in the letterbox bar to the video edge`() {
        // Surface is 2560x1080 (wider than the 1920x1080 video); CONTAIN pillarboxes it to
        // 1920x1080 centered, leaving 320px bars on each side. A touch at the surface's own
        // top-left corner lands inside the left bar, which should clamp to video x=0.
        val point = TouchCoordinateMapper.map(
            rawX = 0f,
            rawY = 0f,
            inputSurfaceWidth = 2560f,
            inputSurfaceHeight = 1080f,
            negotiatedWidth = 1920,
            negotiatedHeight = 1080,
            marginWidth = 0f,
            marginHeight = 0f,
            fitMode = Settings.VideoFitMode.CONTAIN,
            hudMirroring = false
        )

        assertEquals(0, point.x)
        assertEquals(0, point.y)
    }

    @Test
    fun `cover mode crops instead of bars so the same corner maps into cropped video content`() {
        // Same 2560x1080 surface and 1920x1080 video as above, but COVER crops instead of
        // bars: it scales up to 2560x1440 (cropping 180px off the top and bottom), so the
        // surface's top-left corner is 135 video-pixels below the video's actual top edge.
        val point = TouchCoordinateMapper.map(
            rawX = 0f,
            rawY = 0f,
            inputSurfaceWidth = 2560f,
            inputSurfaceHeight = 1080f,
            negotiatedWidth = 1920,
            negotiatedHeight = 1080,
            marginWidth = 0f,
            marginHeight = 0f,
            fitMode = Settings.VideoFitMode.COVER,
            hudMirroring = false
        )

        assertEquals(0, point.x)
        assertEquals(135, point.y)
    }
}
