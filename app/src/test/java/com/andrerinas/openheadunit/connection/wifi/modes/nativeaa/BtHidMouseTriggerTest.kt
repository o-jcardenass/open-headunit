package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class BtHidMouseTriggerTest {

    private lateinit var executor: ScheduledThreadPoolExecutor
    private var now = 1_000_000L
    private val device = mock<BluetoothDevice> {
        on { address } doReturn "AA:BB:CC:DD:EE:FF"
        on { bondState } doReturn BluetoothDevice.BOND_BONDED
    }
    private val adapter = mock<BluetoothAdapter>()

    @Before fun setUp() {
        executor = ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy = true }
    }

    @After fun tearDown() { executor.shutdownNow() }

    private fun trigger() = BtHidMouseTrigger(mock<Context>(), executor, { 30 }, { now })

    @Test fun `stop leaves no scheduled task`() {
        val trigger = trigger()
        trigger.armed = true
        trigger.scheduleRelease(device, 60_000L)
        assertEquals(1, executor.queue.size)

        trigger.stop()

        assertTrue(executor.queue.isEmpty())
    }

    @Test fun `stop never starts a task or arms the trigger`() {
        val trigger = trigger()
        val before = executor.taskCount

        trigger.stop()

        assertEquals(before, executor.taskCount)
        assertFalse(trigger.armed)
    }

    @Test fun `a release is not scheduled while the trigger is stopped`() {
        val trigger = trigger()
        trigger.scheduleRelease(device, 60_000L)
        assertTrue(executor.queue.isEmpty())
    }

    @Test fun `wakes are counted inside the window and forgotten after it`() {
        val trigger = trigger()
        trigger.recordWake("aa:bb:cc:dd:ee:ff")
        trigger.recordWake("AA:BB:CC:DD:EE:FF")
        assertEquals(2, trigger.wakesInWindow("AA:BB:CC:DD:EE:FF"))

        now += HidWakePolicy.WINDOW_MS + 1

        assertEquals(0, trigger.wakesInWindow("AA:BB:CC:DD:EE:FF"))
    }

    private fun hidProxy(accepts: Boolean = true) = mock<BluetoothHidDevice> {
        on { registerApp(any(), anyOrNull(), anyOrNull(), any(), any()) } doReturn accepts
    }

    /** The listener of the [count]th proxy request. */
    private fun proxyListener(count: Int): BluetoothProfile.ServiceListener {
        val captor = argumentCaptor<BluetoothProfile.ServiceListener>()
        verify(adapter, times(count)).getProfileProxy(anyOrNull(), captor.capture(), eq(HID_DEVICE))
        return captor.lastValue
    }

    private fun registerCallback(hid: BluetoothHidDevice, count: Int = 1): BluetoothHidDevice.Callback {
        val captor = argumentCaptor<BluetoothHidDevice.Callback>()
        verify(hid, times(count)).registerApp(any(), anyOrNull(), anyOrNull(), any(), captor.capture())
        return captor.lastValue
    }

    private fun drainExecutor() { executor.submit(Runnable {}).get(5, TimeUnit.SECONDS) }

    @Test fun `stop while register is pending unregisters and a second start registers again`() {
        val trigger = trigger()
        val hid = hidProxy()
        assertTrue(trigger.wake(adapter, device))
        proxyListener(1).onServiceConnected(HID_DEVICE, hid)
        val callback = registerCallback(hid)

        trigger.stop()

        verify(hid).unregisterApp()
        verify(adapter).closeProfileProxy(HID_DEVICE, hid)

        // The registration lands after stop(): it is dropped, not kept.
        callback.onAppStatusChanged(null, true)
        verify(hid, times(2)).unregisterApp()
        verify(hid, never()).connect(any())

        val hid2 = hidProxy()
        assertTrue(trigger.wake(adapter, device))
        assertTrue(trigger.armed)
        proxyListener(2).onServiceConnected(HID_DEVICE, hid2)
        registerCallback(hid2)
    }

    @Test fun `a refused registerApp is asked again on the next wake`() {
        val trigger = trigger()
        val hid = hidProxy(accepts = false)
        trigger.wake(adapter, device)
        proxyListener(1).onServiceConnected(HID_DEVICE, hid)
        registerCallback(hid, 1)

        trigger.wake(adapter, device)

        registerCallback(hid, 2)
    }

    @Test fun `a proxy that arrives after stop is closed on its own adapter`() {
        val trigger = trigger()
        val hid = hidProxy()
        trigger.wake(adapter, device)
        val listener = proxyListener(1)

        trigger.stop()
        listener.onServiceConnected(HID_DEVICE, hid)

        verify(adapter).closeProfileProxy(HID_DEVICE, hid)
        verify(hid, never()).registerApp(any(), anyOrNull(), anyOrNull(), any(), any())
    }

    @Test fun `a connect queued before releaseAll sends nothing`() {
        val trigger = trigger()
        val hid = hidProxy()
        trigger.wake(adapter, device)
        proxyListener(1).onServiceConnected(HID_DEVICE, hid)
        registerCallback(hid).onAppStatusChanged(null, true)
        verify(hid).connect(device)
        clearInvocations(hid)

        val gate = CountDownLatch(1)
        executor.execute { gate.await(5, TimeUnit.SECONDS); Unit }
        trigger.wake(adapter, device)
        trigger.releaseAll()
        gate.countDown()
        drainExecutor()

        verify(hid, never()).connect(any())
    }

    private companion object {
        const val HID_DEVICE = 19
    }
}
