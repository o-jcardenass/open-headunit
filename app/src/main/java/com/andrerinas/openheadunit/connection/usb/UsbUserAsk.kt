package com.andrerinas.openheadunit.connection.usb

import android.os.SystemClock
import androidx.annotation.VisibleForTesting

/**
 * When the user last asked for a USB accessory switch. The phone re-enumerates after the switch
 * and that attach carries no user flag, so the selection gate reads this stamp to let it pass.
 */
object UsbUserAsk {

    @Volatile
    private var stampedAtMs = 0L

    fun stamp() { stampedAtMs = SystemClock.elapsedRealtime() }

    /** Milliseconds since the last stamp, or null when none is set. */
    fun ageMs(): Long? {
        val at = stampedAtMs
        return if (at == 0L) null else SystemClock.elapsedRealtime() - at
    }

    fun clear() { stampedAtMs = 0L }

    @VisibleForTesting
    fun resetForTest() = clear()
}
