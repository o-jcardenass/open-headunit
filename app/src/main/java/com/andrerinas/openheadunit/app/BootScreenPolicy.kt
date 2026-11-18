package com.andrerinas.openheadunit.app

/**
 * Whether a boot or a wake brings the app's screen to the front.
 *
 * "Start on boot" always starts the service. With "Wait in the background" on, it stops there: the
 * service keeps listening, and the screen only shows up once a phone connects and the projection
 * opens. Self Mode and the Wi-Fi server reconnect run only from the home screen, so they open it
 * anyway. USB and Nearby auto-connects run from the service and leave it closed.
 */
object BootScreenPolicy {

    fun opensScreen(startOnBoot: Boolean, waitInBackground: Boolean, homeScreenAutoConnect: Boolean): Boolean =
        startOnBoot && (!waitInBackground || homeScreenAutoConnect)

    /** What only `HomeFragment` runs: Self Mode, and the last-session reconnect to a Wi-Fi server. */
    fun homeScreenAutoConnect(selfMode: Boolean, lastSession: Boolean, lastWasWifi: Boolean, wifiServerMode: Boolean): Boolean =
        selfMode || (lastSession && lastWasWifi && wifiServerMode)
}
