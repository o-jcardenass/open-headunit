package com.andrerinas.openheadunit.connection.wifi

/**
 * Whether the wireless stack stays down while the settings screen is open.
 *
 * A wake poke that works raises the projection over whatever the user is configuring, so the stack
 * stops rather than only deferring its retry loop. One predicate rather than a pause and a resume,
 * because every input can change while the screen is up and the two spellings would drift.
 */
object SettingsScreenPausePolicy {

    /**
     * @param sessionLive a session is never torn down for the settings screen.
     * @param qrHold the setup QR reads the running launcher, so it holds the stack up.
     */
    fun pauses(settingsForeground: Boolean, sessionLive: Boolean, qrHold: Boolean): Boolean =
        settingsForeground && !sessionLive && !qrHold
}
