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

    fun decide(
        settingOn: Boolean,
        macInAutoStartList: Boolean,
        sessionLive: Boolean,
        appVisible: Boolean,
        settingsVisible: Boolean,
        userExitedToOemLauncher: Boolean,
        cancelledByUser: Boolean,
        sinceLastRaiseMs: Long?,
    ): Action = when {
        !settingOn || !macInAutoStartList -> Action.NONE
        !AutoConnectHoldPolicy.raisesUi(settingsVisible, cancelledByUser) -> Action.NONE
        sinceLastRaiseMs != null && sinceLastRaiseMs < MIN_RAISE_INTERVAL_MS -> Action.NONE
        appVisible -> Action.NONE
        !sessionLive -> Action.RAISE_HOME
        userExitedToOemLauncher -> Action.NONE
        else -> Action.RAISE_PROJECTION
    }
}
