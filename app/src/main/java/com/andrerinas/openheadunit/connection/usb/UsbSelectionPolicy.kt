package com.andrerinas.openheadunit.connection.usb

import com.andrerinas.openheadunit.utils.Settings.ConnectionMode

/**
 * The USB mirror of WirelessSelectionPolicy. A hidden plug-in row switched a charging phone
 * under Wi-Fi only. Without USB in the connection type selection, an automatic USB connect is
 * refused unless the user asked for USB. An empty selection counts as USB chosen.
 */
object UsbSelectionPolicy {

    /** One plug-in episode, the same span as the USB episode budget. */
    const val USER_ASK_WINDOW_MS = 60_000L

    /** The grep strings the rig reads in the refusal and pass-on-ask log lines. */
    const val SELECTION_REFUSAL = "USB is not in the connection type selection"
    const val SELECTION_ASK_PASS = "connects after a USB switch the user asked for"

    enum class AutoVerdict { PASS, PASS_ON_ASK, REFUSE }

    fun usbSelected(modes: Set<ConnectionMode>): Boolean =
        modes.isEmpty() || ConnectionMode.USB in modes

    /** [userAskAgeMs] is the age of the last accessory switch the user asked for, null if none. */
    fun automaticVerdict(usbSelected: Boolean, userRequested: Boolean, userAskAgeMs: Long?): AutoVerdict = when {
        usbSelected || userRequested -> AutoVerdict.PASS
        userAskAgeMs != null && userAskAgeMs in 0 until USER_ASK_WINDOW_MS -> AutoVerdict.PASS_ON_ASK
        else -> AutoVerdict.REFUSE
    }

    /** A user connect that names no transport is a USB connect only when USB is chosen. */
    fun refusesRequest(usbSelected: Boolean, userRequested: Boolean, usbNamed: Boolean): Boolean =
        userRequested && !usbNamed && !usbSelected
}
