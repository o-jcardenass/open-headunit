package com.andrerinas.openheadunit.decoder.audio

import android.content.Context
import android.media.AudioManager
import com.andrerinas.openheadunit.utils.AppLog

class AudioManagerVolumePort(
    private val audioManager: AudioManager,
    private val log: (String) -> Unit = AppLog::w
) : VolumePort {
    override fun index(stream: Int): Int = audioManager.getStreamVolume(stream)

    override fun adjust(stream: Int, raise: Boolean) {
        // A ringer-mode change under Do Not Disturb throws; the controller then reads no change.
        try {
            audioManager.adjustStreamVolume(stream, if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        } catch (e: RuntimeException) {
            log("AudioManagerVolumePort: adjust refused on stream $stream: ${e.message}")
        }
    }
}

/** Keeps the held level in a private file so a restart or a power cut adds no drift. */
class PrefsLevelStore(context: Context) : LevelStore {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): StoredLevel? {
        if (!prefs.contains(KEY_STREAM) || !prefs.contains(KEY_INDEX) || !prefs.contains(KEY_LEVEL)) return null
        return StoredLevel(prefs.getInt(KEY_STREAM, 0), prefs.getInt(KEY_INDEX, 0), prefs.getInt(KEY_LEVEL, 0))
    }

    override fun save(value: StoredLevel) {
        prefs.edit()
            .putInt(KEY_STREAM, value.stream)
            .putInt(KEY_INDEX, value.index)
            .putInt(KEY_LEVEL, value.level)
            .apply()
    }

    private companion object {
        const val FILE = "speed_volume_state"
        const val KEY_STREAM = "stream"
        const val KEY_INDEX = "index"
        const val KEY_LEVEL = "level"
    }
}
