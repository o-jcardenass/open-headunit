package com.andrerinas.openheadunit.connection.usb

import com.andrerinas.openheadunit.utils.Settings.ConnectionMode
import com.andrerinas.openheadunit.connection.usb.UsbSelectionPolicy.AutoVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbSelectionPolicyTest {

    private val window = UsbSelectionPolicy.USER_ASK_WINDOW_MS

    @Test fun `an empty selection counts as USB chosen`() {
        assertTrue(UsbSelectionPolicy.usbSelected(emptySet()))
    }

    @Test fun `USB alone or with Wi-Fi is chosen`() {
        assertTrue(UsbSelectionPolicy.usbSelected(setOf(ConnectionMode.USB)))
        assertTrue(UsbSelectionPolicy.usbSelected(setOf(ConnectionMode.WIFI, ConnectionMode.USB)))
    }

    @Test fun `Wi-Fi only or Self only is not USB`() {
        assertFalse(UsbSelectionPolicy.usbSelected(setOf(ConnectionMode.WIFI)))
        assertFalse(UsbSelectionPolicy.usbSelected(setOf(ConnectionMode.SELF)))
    }

    @Test fun `with USB chosen every automatic connect passes whatever the ask age`() {
        assertEquals(AutoVerdict.PASS, UsbSelectionPolicy.automaticVerdict(true, false, null))
        assertEquals(AutoVerdict.PASS, UsbSelectionPolicy.automaticVerdict(true, false, 0L))
        assertEquals(AutoVerdict.PASS, UsbSelectionPolicy.automaticVerdict(true, true, 5L))
    }

    @Test fun `an automatic connect without USB and without an ask is refused`() {
        assertEquals(AutoVerdict.REFUSE, UsbSelectionPolicy.automaticVerdict(false, false, null))
    }

    @Test fun `a user request without USB chosen passes plainly even with a young ask`() {
        assertEquals(AutoVerdict.PASS, UsbSelectionPolicy.automaticVerdict(false, true, null))
        assertEquals(AutoVerdict.PASS, UsbSelectionPolicy.automaticVerdict(false, true, 10L))
    }

    @Test fun `a young ask lets an automatic connect through as PASS_ON_ASK`() {
        assertEquals(AutoVerdict.PASS_ON_ASK, UsbSelectionPolicy.automaticVerdict(false, false, 0L))
        assertEquals(AutoVerdict.PASS_ON_ASK, UsbSelectionPolicy.automaticVerdict(false, false, window - 1))
    }

    @Test fun `an ask as old as the window no longer passes`() {
        assertEquals(AutoVerdict.REFUSE, UsbSelectionPolicy.automaticVerdict(false, false, window))
        assertEquals(AutoVerdict.REFUSE, UsbSelectionPolicy.automaticVerdict(false, false, window + 1))
    }

    @Test fun `a negative ask age never passes`() {
        assertEquals(AutoVerdict.REFUSE, UsbSelectionPolicy.automaticVerdict(false, false, -1L))
    }

    @Test fun `a user request that names no transport is refused without USB`() {
        assertTrue(UsbSelectionPolicy.refusesRequest(false, true, false))
    }

    @Test fun `a user request that names USB passes without USB chosen`() {
        assertFalse(UsbSelectionPolicy.refusesRequest(false, true, true))
    }

    @Test fun `a generic user request passes when USB is chosen`() {
        assertFalse(UsbSelectionPolicy.refusesRequest(true, true, false))
    }

    @Test fun `a request that is not from the user is left to the automatic gate`() {
        assertFalse(UsbSelectionPolicy.refusesRequest(false, false, false))
    }
}
