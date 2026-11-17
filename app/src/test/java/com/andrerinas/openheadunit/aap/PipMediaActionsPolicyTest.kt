package com.andrerinas.openheadunit.aap

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PipMediaActionsPolicyTest {

    @Test fun `setting off gives no actions`() {
        assertTrue(PipMediaActionsPolicy.actions(enabled = false, apiLevel = 34).isEmpty())
    }

    @Test fun `api below 26 gives no actions`() {
        assertTrue(PipMediaActionsPolicy.actions(enabled = true, apiLevel = 25).isEmpty())
    }

    @Test fun `api 26 and on gives previous, play pause and next in order`() {
        val keys = PipMediaActionsPolicy.actions(enabled = true, apiLevel = 26).map { it.keyCode }
        assertEquals(
            listOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_NEXT),
            keys
        )
    }

    @Test fun `playing shows pause and paused shows play`() {
        assertEquals(PipMediaActionsPolicy.PlayPauseIcon.PAUSE, PipMediaActionsPolicy.playPauseIcon(true))
        assertEquals(PipMediaActionsPolicy.PlayPauseIcon.PLAY, PipMediaActionsPolicy.playPauseIcon(false))
    }
}
