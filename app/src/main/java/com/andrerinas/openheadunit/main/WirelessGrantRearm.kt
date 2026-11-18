package com.andrerinas.openheadunit.main

import android.content.Context
import android.os.Build
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.Settings

/** The one re-arm every screen that asks for a wireless grant runs once the result lands. */
internal object WirelessGrantRearm {
    fun afterGrant(context: Context, granted: Collection<String>, tag: String) {
        val settings = Settings(context)
        val wirelessOn = settings.wifiConnectionMode != WifiLauncherMode.MANUAL && settings.showsWifi()
        if (WirelessGrantRearmPolicy.rearms(granted, wirelessOn, Build.VERSION.SDK_INT)) {
            AppLog.i("$tag: a wireless permission was granted, re-arming the wireless mode")
            AapService.applyWirelessSettings(context, startWireless = true, forceRearm = true)
        }
    }
}
