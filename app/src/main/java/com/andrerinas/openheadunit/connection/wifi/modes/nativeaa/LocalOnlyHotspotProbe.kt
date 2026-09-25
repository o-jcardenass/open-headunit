package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog

/**
 * Probe, never merged: does a LocalOnlyHotspot take this unit's station down, and so stop its scans?
 * Hands the reservation's credentials to the Hotspot transport through the manual overrides.
 */
object LocalOnlyHotspotProbe {

    private const val HEARTBEAT_MS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var startedAtMs = 0L
    private var scans = 0
    private var receiverRegistered = false
    private var savedSsid: String? = null
    private var savedPassword: String? = null
    private var appContext: Context? = null

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            scans++
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            AppLog.i("LohsProbe: station scan #$scans at +${sinceStartS()}s (updated=$updated), ${stationState(context)}")
        }
    }

    private val heartbeat = object : Runnable {
        override fun run() {
            val context = appContext ?: return
            AppLog.i("LohsProbe: heartbeat +${sinceStartS()}s, scans=$scans, held=${reservation != null}, ${stationState(context)}")
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            AppLog.w("LohsProbe: LocalOnlyHotspot needs API 26, this is ${Build.VERSION.SDK_INT}")
            return false
        }
        val app = context.applicationContext
        appContext = app
        if (reservation != null) {
            AppLog.i("LohsProbe: already holding a reservation")
            return true
        }
        val wm = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        AppLog.i("LohsProbe: before start, ${stationState(app)}, staApConcurrency=${staApConcurrency(wm)}")
        registerScans(app)
        startedAtMs = System.currentTimeMillis()
        scans = 0
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, HEARTBEAT_MS)
        return try {
            wm.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    onReservation(app, wm, res)
                }

                override fun onStopped() {
                    AppLog.w("LohsProbe: the platform stopped the hotspot at +${sinceStartS()}s")
                    reservation = null
                }

                override fun onFailed(reason: Int) {
                    AppLog.e("LohsProbe: start failed, reason=$reason (${failureName(reason)})")
                    stop(app)
                }
            }, handler)
            true
        } catch (e: Exception) {
            AppLog.e("LohsProbe: startLocalOnlyHotspot threw: ${e.javaClass.simpleName}: ${e.message}")
            stop(app)
            false
        }
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        handler.removeCallbacks(heartbeat)
        try { reservation?.close() } catch (e: Exception) {}
        if (reservation != null) AppLog.i("LohsProbe: reservation closed after ${sinceStartS()}s, scans=$scans")
        reservation = null
        if (receiverRegistered) {
            // Cleared even if unregister throws, so a later start can register again.
            try { app.unregisterReceiver(scanReceiver) } catch (e: Exception) {}
            receiverRegistered = false
        }
        val settings = App.provide(app).settings
        savedSsid?.let { settings.hotspotSsid = it }
        savedPassword?.let { settings.hotspotPassword = it }
        savedSsid = null
        savedPassword = null
    }

    @Suppress("DEPRECATION")
    private fun onReservation(context: Context, wm: WifiManager, res: WifiManager.LocalOnlyHotspotReservation) {
        var ssid: String? = null
        var psk: String? = null
        var bssid: String? = null
        var band = "unreadable"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val config = res.softApConfiguration
            ssid = config.ssid
            psk = config.passphrase
            bssid = config.bssid?.toString()
            // getBand() is hidden on the public SDK; the frequency in dumpsys is the real answer.
            band = try {
                config.javaClass.getMethod("getBand").invoke(config).toString()
            } catch (e: Exception) { "unreadable (${e.javaClass.simpleName})" }
        } else {
            val config = res.wifiConfiguration
            ssid = config?.SSID
            psk = config?.preSharedKey
            bssid = config?.BSSID
        }
        AppLog.i(
            "LohsProbe: started at +${sinceStartS()}s ssid=$ssid pskLength=${psk?.length ?: 0} " +
                "bssid=$bssid bandMask=$band staApConcurrency=${staApConcurrency(wm)}, ${stationState(context)}"
        )
        if (ssid.isNullOrEmpty() || psk.isNullOrEmpty()) {
            AppLog.w("LohsProbe: no credentials to hand the Hotspot transport")
            return
        }
        val settings = App.provide(context).settings
        if (savedSsid == null) savedSsid = settings.hotspotSsid
        if (savedPassword == null) savedPassword = settings.hotspotPassword
        settings.hotspotSsid = ssid.removeSurrounding("\"")
        settings.hotspotPassword = psk.removeSurrounding("\"")
        AppLog.i("LohsProbe: handed '${settings.hotspotSsid}' to the Hotspot transport's manual overrides")
    }

    private fun registerScans(context: Context) {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                context, scanReceiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        } catch (e: Exception) {
            AppLog.w("LohsProbe: could not watch scans: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun stationState(context: Context): String = try {
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        "station wifiEnabled=${wm.isWifiEnabled} supplicant=${wm.connectionInfo?.supplicantState}"
    } catch (e: Exception) {
        "station unreadable (${e.javaClass.simpleName})"
    }

    private fun staApConcurrency(wm: WifiManager): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wm.isStaApConcurrencySupported.toString() else "unknown (api<30)"

    private fun sinceStartS(): Long =
        if (startedAtMs == 0L) 0 else (System.currentTimeMillis() - startedAtMs) / 1000

    private fun failureName(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "no channel"
        WifiManager.LocalOnlyHotspotCallback.ERROR_GENERIC -> "generic"
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "incompatible mode (tethering on?)"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "tethering disallowed"
        else -> "unknown"
    }
}
