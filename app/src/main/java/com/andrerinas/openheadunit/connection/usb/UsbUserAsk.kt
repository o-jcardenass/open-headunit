package com.andrerinas.openheadunit.connection.usb

import android.os.SystemClock
import androidx.annotation.VisibleForTesting

/**
 * When the user last asked for a USB accessory switch. The phone re-enumerates after the switch
 * and that attach carries no user flag, so the selection gate reads this stamp to let it pass.
 */
object UsbUserAsk {

    private const val NONE = -1L

    /** A test replaces it: the stamp is set on an IO thread, where a static SystemClock mock is not live. */
    @VisibleForTesting
    var clock: () -> Long = { SystemClock.elapsedRealtime() }

    @Volatile
    private var stampedAtMs = NONE

    fun stamp() { stampedAtMs = clock() }

    /** Milliseconds since the last stamp, or null when none is set. */
    fun ageMs(): Long? {
        val at = stampedAtMs
        return if (at == NONE) null else clock() - at
    }

    fun clear() { stampedAtMs = NONE }

    @VisibleForTesting
    fun resetForTest() = clear()
}
