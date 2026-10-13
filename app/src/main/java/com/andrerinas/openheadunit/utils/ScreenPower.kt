package com.andrerinas.openheadunit.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.andrerinas.openheadunit.app.ScreenOffAdminReceiver

object ScreenPower {

    /** True when the screen is lit, null when it cannot be read. */
    fun isInteractive(context: Context): Boolean? = try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 20) pm.isInteractive else pm.isScreenOn
    } catch (e: Exception) {
        null
    }

    fun adminComponent(context: Context) = ComponentName(context, ScreenOffAdminReceiver::class.java)

    /** True when the user has granted the force-lock device admin. */
    fun hasLockGrant(context: Context): Boolean = try {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        dpm.isAdminActive(adminComponent(context))
    } catch (e: Exception) {
        false
    }

    /** Locks and darkens the screen. False without the grant, or when the system refuses. */
    fun turnOff(context: Context): Boolean {
        if (!hasLockGrant(context)) return false
        return try {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).lockNow()
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** A two-second bright wake lock that lights a dark panel; on a lit one it shows nothing. */
    @Suppress("DEPRECATION")
    fun wakeBriefly(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE
            pm.newWakeLock(flags, "HeadunitRevived::ExternalPowerWake").acquire(2_000)
        } catch (_: Exception) {
        }
    }
}
