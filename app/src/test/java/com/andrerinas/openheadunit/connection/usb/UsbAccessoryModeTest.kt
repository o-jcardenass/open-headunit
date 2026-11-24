package com.andrerinas.openheadunit.connection.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.andrerinas.openheadunit.utils.AppLog
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class UsbAccessoryModeTest {

    private val oldLogger = AppLog.LOGGER
    private val oldClock = UsbUserAsk.clock

    @Before fun setUp() {
        AppLog.LOGGER = object : AppLog.Logger {
            override fun println(priority: Int, tag: String, msg: String) = Unit
        }
        UsbUserAsk.clock = { 1_000L }
        UsbUserAsk.resetForTest()
        UsbPermissionAsks.resetForTest()
    }

    @After fun tearDown() {
        UsbUserAsk.resetForTest()
        UsbUserAsk.clock = oldClock
        AppLog.LOGGER = oldLogger
    }

    private fun phone(protocolLen: Int, onStart: () -> Unit = {}): Pair<UsbManager, UsbDevice> {
        val manager = mock(UsbManager::class.java)
        val device = mock(UsbDevice::class.java)
        val connection = mock(UsbDeviceConnection::class.java)
        `when`(manager.openDevice(device)).thenReturn(connection)
        `when`(connection.controlTransfer(anyInt(), eq(51), anyInt(), anyInt(), any(), anyInt(), anyInt()))
            .thenAnswer { (it.arguments[4] as ByteArray)[0] = 2; protocolLen }
        `when`(connection.controlTransfer(anyInt(), eq(52), anyInt(), anyInt(), any(), anyInt(), anyInt()))
            .thenAnswer { it.arguments[5] as Int }
        `when`(connection.controlTransfer(anyInt(), eq(53), anyInt(), anyInt(), any(), anyInt(), anyInt()))
            .thenAnswer { onStart(); 0 }
        return manager to device
    }

    @Test fun theStampLandsBeforeTheSwitchReturnsSoAnEarlyAttachPassesTheGate() {
        val started = CountDownLatch(1)
        val (manager, device) = phone(protocolLen = 2) { started.countDown() }
        var result: Boolean? = null
        val switching = Thread { result = UsbAccessoryMode(manager).connectAndSwitch(device) }
        switching.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        // The switch waits 500 ms for re-enumeration; an attach inside that wait must see the stamp.
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300)
        while (UsbUserAsk.ageMs() == null && System.nanoTime() < deadline) Thread.sleep(5)
        assertTrue("the switch returned before the check", switching.isAlive)
        assertNotNull(UsbUserAsk.ageMs())
        switching.join(5_000)
        assertTrue(result == true)
    }

    @Test fun aSwitchThatSentNoStartLeavesNoStamp() {
        val (manager, device) = phone(protocolLen = -1)
        assertFalse(UsbAccessoryMode(manager).connectAndSwitch(device))
        assertNull(UsbUserAsk.ageMs())
    }
}
