package com.andrerinas.openheadunit.decoder.audio

import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi

/** Keeps the routed device name for the mixer thread: the lookup is a binder call, so it runs on the main looper. */
@RequiresApi(24)
internal class AudioTrackRouteWatch(private val track: AudioTrack) {
    @Volatile var label: String? = null
        private set
    private val handler = Handler(Looper.getMainLooper())
    private val read = Runnable {
        try {
            label = track.getRoutedDevice()?.let { "${it.productName} type=${it.type}" }
        } catch (_: Exception) { }
    }
    // A typed object, not a lambda: AudioTrack also has a deprecated overload of the add call.
    private val listener = object : AudioRouting.OnRoutingChangedListener {
        override fun onRoutingChanged(router: AudioRouting) { read.run() }
    }

    fun attach() { track.addOnRoutingChangedListener(listener, handler) }

    fun refresh() { handler.post(read) }

    fun detach() {
        try { track.removeOnRoutingChangedListener(listener) } catch (_: Exception) { }
        handler.removeCallbacks(read)
    }
}
