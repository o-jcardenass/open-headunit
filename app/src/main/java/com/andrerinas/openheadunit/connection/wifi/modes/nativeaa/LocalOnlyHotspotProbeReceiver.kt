package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.andrerinas.openheadunit.utils.AppLog

/** Probe, never merged: a rig script starts or stops a LocalOnlyHotspot with `am broadcast`. */
class LocalOnlyHotspotProbeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppLog.i("LohsProbe: ${intent.action}")
        val ok = when (intent.action) {
            ACTION_START -> LocalOnlyHotspotProbe.start(context)
            ACTION_STOP -> { LocalOnlyHotspotProbe.stop(context); true }
            else -> false
        }
        if (isOrderedBroadcast) {
            resultCode = if (ok) 0 else 1
            resultData = "ok=$ok"
        }
    }

    companion object {
        const val ACTION_START = "com.andrerinas.openheadunit.PROBE_LOHS_START"
        const val ACTION_STOP = "com.andrerinas.openheadunit.PROBE_LOHS_STOP"
    }
}
