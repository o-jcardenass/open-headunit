package com.andrerinas.openheadunit.main

import android.Manifest
import android.annotation.SuppressLint

/**
 * Whether a runtime permission grant re-arms the wireless mode. A restore can start wireless
 * before the Bluetooth, Nearby or (below API 33) location grant exists; the re-arm is then forced.
 */
object WirelessGrantRearmPolicy {
    // Names only: compared as text, so an older platform never resolves them.
    @SuppressLint("InlinedApi")
    private val wirelessPermissions = setOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.NEARBY_WIFI_DEVICES,
    )

    /** Below this API level, Nearby and Wi-Fi Direct need the fine location grant. */
    private const val NEARBY_DEVICES_SDK = 33

    /** @param wirelessOn the wireless mode is not Manual and the setup shows Wi-Fi. */
    fun rearms(granted: Collection<String>, wirelessOn: Boolean, sdkInt: Int): Boolean =
        wirelessOn && granted.any {
            it in wirelessPermissions || (it == Manifest.permission.ACCESS_FINE_LOCATION && sdkInt < NEARBY_DEVICES_SDK)
        }

    /** A forced re-arm rebuilds the launcher, so a live or connecting session keeps it unforced. */
    fun forces(requested: Boolean, sessionLive: Boolean): Boolean = requested && !sessionLive
}
