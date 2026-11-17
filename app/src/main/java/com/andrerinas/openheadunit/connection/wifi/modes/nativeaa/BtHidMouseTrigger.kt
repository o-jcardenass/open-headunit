package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.andrerinas.openheadunit.utils.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Wakes a sleeping phone with a Bluetooth HID nudge (API 28+): connect to a bonded wake target, send
 * Left-Shift and a mouse jiggle, disconnect 3 s later so the phone's keyboard returns. One instance
 * per `AapService`. The caller decides through [HidWakePolicy]; this class never pairs.
 */
@SuppressLint("MissingPermission", "NewApi")
class BtHidMouseTrigger(
    private val context: Context,
    private val executor: ScheduledExecutorService = defaultExecutor(),
    private val apiLevel: () -> Int = { Build.VERSION.SDK_INT },
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {

    // Typed as the API 11 interface so this class loads on a unit below API 28.
    @Volatile private var hid: BluetoothProfile? = null
    @Volatile private var registered = false
    @Volatile private var proxyRequested = false
    @Volatile private var registerRequested = false
    @Volatile private var adapter: BluetoothAdapter? = null
    /** The listener of the live proxy request. A proxy from any other listener is closed. */
    @Volatile private var proxyListener: ProxyListener? = null
    @Volatile internal var armed = false

    /** Phones asked to wake and not yet released, by address. */
    private val wanted = ConcurrentHashMap<String, BluetoothDevice>()
    private val releaseTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val wakeStamps = ConcurrentHashMap<String, MutableList<Long>>()

    /** Wakes sent to [address] inside the policy window, for [HidWakePolicy.mayWake]. */
    fun wakesInWindow(address: String): Int {
        val stamps = wakeStamps[address.uppercase()] ?: return 0
        synchronized(stamps) { return HidWakePolicy.countInWindow(stamps, nowMs()) }
    }

    internal fun recordWake(address: String) {
        val stamps = wakeStamps.getOrPut(address.uppercase()) { mutableListOf() }
        val now = nowMs()
        synchronized(stamps) {
            stamps.removeAll { now - it !in 0 until HidWakePolicy.WINDOW_MS }
            stamps.add(now)
        }
    }

    /**
     * Asks for one wake of [device]. The caller has already passed [HidWakePolicy.mayWake].
     * Returns false where HID cannot run at all.
     */
    fun wake(adapter: BluetoothAdapter, device: BluetoothDevice): Boolean {
        if (apiLevel() < HidWakePolicy.MIN_API || !hasConnectPermission()) return false
        val address = device.address.uppercase()
        recordWake(address)
        wanted[address] = device
        start(adapter)
        if (registered) executor.execute { connectAndWake(device) }
        return true
    }

    /** Opens the HID proxy and registers the keyboard and mouse record. Safe to call again. */
    private fun start(adapter: BluetoothAdapter) {
        armed = true
        this.adapter = adapter
        val proxy = hid
        if (proxy == null) {
            if (proxyRequested) return
            proxyRequested = true
            val listener = ProxyListener(adapter)
            proxyListener = listener
            try {
                AppLog.i("NativeAA-HID: requesting the HID_DEVICE proxy")
                adapter.getProfileProxy(context.applicationContext, listener, PROFILE_HID_DEVICE)
            } catch (e: Exception) {
                proxyRequested = false
                proxyListener = null
                AppLog.w("NativeAA-HID: could not request the HID device profile: ${e.message}")
            }
        } else if (!registered && !registerRequested) {
            requestRegistration(proxy as BluetoothHidDevice)
        }
    }

    /** Drops every HID link now and forgets the pending wakes. The record stays registered. */
    fun releaseAll() {
        releaseTasks.values.forEach { it.cancel(false) }
        releaseTasks.clear()
        wanted.clear()
        val proxy = hid
        if (apiLevel() < HidWakePolicy.MIN_API || proxy == null) return
        try {
            for (device in proxy.getDevicesMatchingConnectionStates(LIVE_STATES)) {
                disconnect(device, "session or stop")
            }
        } catch (e: Exception) {
            AppLog.d("NativeAA-HID: release failed: ${e.message}")
        }
    }

    /**
     * Cancels every task, drops the links and unregisters the record, also one still pending.
     * It never starts anything: [wake] restores `armed`, the proxy and the registration.
     */
    fun stop() {
        armed = false
        releaseAll()
        val proxy = hid
        val proxyAdapter = adapter
        // The service grants one HID app per device; a pending registration left behind blocks the next.
        val owned = registered || registerRequested
        hid = null
        registered = false
        proxyRequested = false
        registerRequested = false
        proxyListener = null
        adapter = null
        if (apiLevel() >= HidWakePolicy.MIN_API && proxy != null) {
            try { if (owned) (proxy as BluetoothHidDevice).unregisterApp() } catch (_: Exception) {}
            try { proxyAdapter?.closeProfileProxy(PROFILE_HID_DEVICE, proxy) } catch (_: Exception) {}
        }
    }

    private fun hasConnectPermission(): Boolean =
        apiLevel() < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context.applicationContext, android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.P)
    private fun connectAndWake(device: BluetoothDevice) {
        val proxy = hid as? BluetoothHidDevice ?: return
        val addr = device.address.uppercase()
        // A connect queued before releaseAll() or stop() must not open a link.
        if (!armed || !wanted.containsKey(addr)) return
        try {
            // A bond lost since the policy check must not turn into a pairing request.
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                wanted.remove(addr)
                AppLog.i("NativeAA-HID: $addr is not bonded; no wake")
                return
            }
            when (val state = proxy.getConnectionState(device)) {
                BluetoothProfile.STATE_CONNECTED -> {
                    sendWakeSequence(proxy, device)
                    scheduleRelease(device, HidWakePolicy.DISCONNECT_AFTER_MS)
                }
                BluetoothProfile.STATE_CONNECTING ->
                    AppLog.d("NativeAA-HID: STATE_CONNECTING to $addr; waiting")
                else -> {
                    val requested = proxy.connect(device)
                    AppLog.i("NativeAA-HID: hid.connect($addr, prevState=$state) -> $requested")
                    // A connect that never completes is dropped after this window.
                    scheduleRelease(device, CONNECT_WINDOW_MS)
                }
            }
        } catch (e: Exception) {
            AppLog.d("NativeAA-HID: connectAndWake($addr) failed: ${e.message}")
        }
    }

    internal fun scheduleRelease(device: BluetoothDevice, delayMs: Long) {
        if (!armed) return
        val addr = device.address.uppercase()
        val task = executor.schedule({
            releaseTasks.remove(addr)
            wanted.remove(addr)
            disconnect(device, "short link ended")
        }, delayMs, TimeUnit.MILLISECONDS)
        releaseTasks.put(addr, task)?.cancel(false)
    }

    private fun disconnect(device: BluetoothDevice, why: String) {
        if (apiLevel() < HidWakePolicy.MIN_API) return
        try {
            val proxy = hid as? BluetoothHidDevice ?: return
            if (proxy.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) return
            val requested = proxy.disconnect(device)
            AppLog.i("NativeAA-HID: hid.disconnect(${device.address}) -> $requested ($why)")
        } catch (e: Exception) {
            AppLog.d("NativeAA-HID: disconnect(${device.address}) failed: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendWakeSequence(proxy: BluetoothHidDevice, device: BluetoothDevice) {
        val addr = device.address.uppercase()
        // Repeated so the phone's uhid node is ready for at least one of them.
        sendWakeReports(proxy, device)
        for (delayMs in longArrayOf(300L, 700L)) {
            executor.schedule({
                if (armed && wanted.containsKey(addr)) sendWakeReports(proxy, device)
            }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendWakeReports(proxy: BluetoothHidDevice, device: BluetoothDevice) {
        try {
            if (proxy.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) return
            // Mouse +2 then -2 on X, zero net movement.
            proxy.sendReport(device, REPORT_ID_MOUSE, byteArrayOf(0x00, 0x02, 0x00))
            proxy.sendReport(device, REPORT_ID_MOUSE, byteArrayOf(0x00, 0xFE.toByte(), 0x00))
            // Left-Shift down then up: wakes the lock screen without typing.
            proxy.sendReport(device, REPORT_ID_KEYBOARD, byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            proxy.sendReport(device, REPORT_ID_KEYBOARD, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            AppLog.i("NativeAA-HID: sent keyboard+mouse wake reports to ${device.address}")
        } catch (e: Exception) {
            AppLog.d("NativeAA-HID: wake report to ${device.address} failed: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun requestRegistration(proxy: BluetoothHidDevice) {
        registerRequested = true
        if (!registerHidApp(proxy)) registerRequested = false
    }

    /** Returns false when the service refused the record, so the next wake asks again. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun registerHidApp(proxy: BluetoothHidDevice): Boolean {
        return try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "OpenHeadunit HID Trigger",
                "Serverless Keyboard & Mouse",
                "OpenHeadunit",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                COMBO_HID_DESCRIPTOR
            )
            val called = proxy.registerApp(sdp, null, null, executor, HidCallback(proxy))
            if (called) {
                AppLog.i("NativeAA-HID: registerApp call returned true")
            } else {
                AppLog.w("NativeAA-HID: registerApp refused; the next wake asks again")
            }
            called
        } catch (e: Exception) {
            AppLog.w("NativeAA-HID: registerApp failed: ${e.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private inner class HidCallback(private val proxy: BluetoothHidDevice) : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (!armed || hid !== proxy) {
                // A registration that lands after stop() has no owner: drop it at once.
                if (registered) {
                    val dropped = try { proxy.unregisterApp() } catch (_: Exception) { false }
                    AppLog.i("NativeAA-HID: late registration after stop; unregisterApp -> $dropped")
                }
                return
            }
            this@BtHidMouseTrigger.registered = registered
            if (!registered) registerRequested = false
            AppLog.i("NativeAA-HID: keyboard+mouse registered=$registered")
            if (registered && armed) wanted.values.forEach { connectAndWake(it) }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            if (device == null) return
            val addr = device.address.uppercase()
            AppLog.i("NativeAA-HID: connection state for $addr -> $state")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (wanted.containsKey(addr)) sendWakeSequence(proxy, device)
                    // Any HID link, ours or the phone's, ends 3 s after it comes up.
                    scheduleRelease(device, HidWakePolicy.DISCONNECT_AFTER_MS)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    wanted.remove(addr)
                    releaseTasks.remove(addr)?.cancel(false)
                }
            }
        }
    }

    /** One per proxy request, so a proxy that arrives after stop() closes on its own adapter. */
    private inner class ProxyListener(private val source: BluetoothAdapter) : BluetoothProfile.ServiceListener {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != PROFILE_HID_DEVICE) return
            if (!armed || proxyListener !== this) {
                AppLog.i("NativeAA-HID: HID_DEVICE proxy arrived after stop; closing it")
                try { source.closeProfileProxy(PROFILE_HID_DEVICE, proxy) } catch (_: Exception) {}
                return
            }
            val device = proxy as? BluetoothHidDevice ?: return
            hid = device
            if (!registerRequested) requestRegistration(device)
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != PROFILE_HID_DEVICE || proxyListener !== this) return
            AppLog.w("NativeAA-HID: HID_DEVICE profile service disconnected")
            hid = null
            registered = false
            proxyRequested = false
            registerRequested = false
            proxyListener = null
        }
    }

    companion object {
        /** BluetoothProfile.HID_DEVICE, public since API 28. */
        private const val PROFILE_HID_DEVICE = 19
        private const val REPORT_ID_MOUSE = 1
        private const val REPORT_ID_KEYBOARD = 2
        private const val CONNECT_WINDOW_MS = 15_000L
        private val LIVE_STATES = intArrayOf(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING)

        /** One daemon thread that exits when idle, so a destroyed service leaves nothing behind. */
        private fun defaultExecutor(): ScheduledExecutorService =
            ScheduledThreadPoolExecutor(1) { job ->
                Thread(job, "bt_hid_trigger").apply { isDaemon = true }
            }.apply {
                setKeepAliveTime(5, TimeUnit.SECONDS)
                allowCoreThreadTimeOut(true)
            }

        /**
         * Composite HID Report Descriptor:
         * - Report ID 1: 3-Button Mouse (3 bytes: buttons, X, Y)
         * - Report ID 2: Standard Boot Keyboard (8 bytes: modifier, reserved, 6 keycodes)
         */
        private val COMBO_HID_DESCRIPTOR = intArrayOf(
            // --- Mouse (Report ID 1) ---
            0x05, 0x01,       // Usage Page (Generic Desktop)
            0x09, 0x02,       // Usage (Mouse)
            0xA1, 0x01,       // Collection (Application)
            0x85, REPORT_ID_MOUSE, // Report ID (1)
            0x09, 0x01,       //   Usage (Pointer)
            0xA1, 0x00,       //   Collection (Physical)
            0x05, 0x09,       //     Usage Page (Buttons)
            0x19, 0x01,       //     Usage Minimum (1)
            0x29, 0x03,       //     Usage Maximum (3)
            0x15, 0x00,       //     Logical Minimum (0)
            0x25, 0x01,       //     Logical Maximum (1)
            0x95, 0x03,       //     Report Count (3)
            0x75, 0x01,       //     Report Size (1)
            0x81, 0x02,       //     Input (Data, Variable, Absolute)
            0x95, 0x01,       //     Report Count (1)
            0x75, 0x05,       //     Report Size (5)
            0x81, 0x01,       //     Input (Constant)
            0x05, 0x01,       //     Usage Page (Generic Desktop)
            0x09, 0x30,       //     Usage (X)
            0x09, 0x31,       //     Usage (Y)
            0x15, 0x81,       //     Logical Minimum (-127)
            0x25, 0x7F,       //     Logical Maximum (127)
            0x75, 0x08,       //     Report Size (8)
            0x95, 0x02,       //     Report Count (2)
            0x81, 0x06,       //     Input (Data, Variable, Relative)
            0xC0,             //   End Collection
            0xC0,             // End Collection

            // --- Keyboard (Report ID 2) ---
            0x05, 0x01,       // Usage Page (Generic Desktop)
            0x09, 0x06,       // Usage (Keyboard)
            0xA1, 0x01,       // Collection (Application)
            0x85, REPORT_ID_KEYBOARD, // Report ID (2)
            0x05, 0x07,       //   Usage Page (Key Codes)
            0x19, 0xE0,       //   Usage Minimum (224 - Left Control)
            0x29, 0xE7,       //   Usage Maximum (231 - Right GUI)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x01,       //   Logical Maximum (1)
            0x75, 0x01,       //   Report Size (1)
            0x95, 0x08,       //   Report Count (8)
            0x81, 0x02,       //   Input (Data, Variable, Absolute) - Modifier byte
            0x95, 0x01,       //   Report Count (1)
            0x75, 0x08,       //   Report Size (8)
            0x81, 0x01,       //   Input (Constant) - Reserved byte
            0x95, 0x06,       //   Report Count (6)
            0x75, 0x08,       //   Report Size (8)
            0x15, 0x00,       //   Logical Minimum (0)
            0x25, 0x65,       //   Logical Maximum (101)
            0x05, 0x07,       //   Usage Page (Key Codes)
            0x19, 0x00,       //   Usage Minimum (0)
            0x29, 0x65,       //   Usage Maximum (101)
            0x81, 0x00,       //   Input (Data, Array) - Key array (6 keys)
            0xC0              // End Collection
        ).map { it.toByte() }.toByteArray()
    }
}
