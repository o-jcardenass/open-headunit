package com.andrerinas.openheadunit.aap

import android.view.KeyEvent

/** Which media buttons the picture-in-picture window carries, and the play/pause icon. */
object PipMediaActionsPolicy {

    enum class PlayPauseIcon { PLAY, PAUSE }

    data class Action(val keyCode: Int)

    /** RemoteAction and PictureInPictureParams.setActions need API 26. */
    private const val MIN_API = 26

    /** Previous, play/pause and next in order; empty when the setting is off or the API is too low. */
    fun actions(enabled: Boolean, apiLevel: Int): List<Action> =
        if (!enabled || apiLevel < MIN_API) emptyList()
        else listOf(
            Action(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            Action(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
            Action(KeyEvent.KEYCODE_MEDIA_NEXT),
        )

    /** Playing shows the pause icon, because a tap pauses. */
    fun playPauseIcon(isPlaying: Boolean): PlayPauseIcon =
        if (isPlaying) PlayPauseIcon.PAUSE else PlayPauseIcon.PLAY
}
