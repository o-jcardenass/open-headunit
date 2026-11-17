package com.andrerinas.openheadunit.app

import com.andrerinas.openheadunit.connection.AutoConnectHoldPolicy

/**
 * Whether a Bluetooth arrival of an auto-start phone raises the app, and which screen.
 * Only the auto-start list counts: the wake list and the preferred device are filled by the app itself.
 */
object BtRaisePolicy {

    enum class Action { NONE, RAISE_HOME, RAISE_PROJECTION }

    /** An ACL can flap, so one phone raises at most once in this time. */
    const val MIN_RAISE_INTERVAL_MS = 10_000L

    /** A raise check that fires this late slept through its deadline, so its arrival is stale. */
    const val STALE_CHECK_MS = 3_000L

    fun decide(
        settingOn: Boolean,
        macInAutoStartList: Boolean,
        sessionLive: Boolean,
        appVisible: Boolean,
        settingsVisible: Boolean,
        userExitedToOemLauncher: Boolean,
        cancelledByUser: Boolean,
        sinceLastRaiseMs: Long?,
        pokeInFlight: Boolean,
    ): Action = when {
        !settingOn || !macInAutoStartList -> Action.NONE
        // Our own poke opens an ACL to the phone, so that arrival is not the phone's.
        pokeInFlight -> Action.NONE
        !AutoConnectHoldPolicy.raisesUi(settingsVisible, cancelledByUser) -> Action.NONE
        sinceLastRaiseMs != null && sinceLastRaiseMs < MIN_RAISE_INTERVAL_MS -> Action.NONE
        appVisible -> Action.NONE
        !sessionLive -> Action.RAISE_HOME
        userExitedToOemLauncher -> Action.NONE
        else -> Action.RAISE_PROJECTION
    }

    /**
     * After a raise from the background: true when the fallback must run. A foreground start
     * since the raise means it landed, even if the user left again before the check.
     */
    fun needsFallback(lastForegroundAtMs: Long, raisedAtMs: Long, checkDeadlineMs: Long, nowMs: Long): Boolean =
        lastForegroundAtMs < raisedAtMs && nowMs - checkDeadlineMs < STALE_CHECK_MS
}
